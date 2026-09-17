package com.danielhoop.timestables

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { OPERATOR, SETUP, PRACTICE, RESULTS, SETTINGS }

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    var screen by mutableStateOf(AppScreen.OPERATOR)
        private set
    var operator by mutableStateOf(MathOperator.MULTIPLY)
        private set
    var highestNumberText by mutableStateOf("10")
        private set
    var randomFirstSecond by mutableStateOf(true)
        private set
    var withoutOneAndTen by mutableStateOf(true)
        private set
    var autoEnter by mutableStateOf(false)
        private set
    var orderedNumbers by mutableStateOf(false)
        private set
    var highestNumberError by mutableStateOf<String?>(null)
        private set
    var isConfigurationLoading by mutableStateOf(false)
        private set
    var scoresByFirstNumber by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set
    var timerMinutes by mutableIntStateOf(30)
        private set
    var timerIsArmed by mutableStateOf(false)
        private set
    var remainingTimerSeconds by mutableIntStateOf(30 * 60)
        private set
    var showTimeUpDialog by mutableStateOf(false)
        private set
    var timerHistory by mutableStateOf<List<TimerHistoryEntry>>(emptyList())
        private set
    var isTimerHistoryLoading by mutableStateOf(false)
        private set
    var currentCalculation by mutableStateOf<Calculation?>(null)
        private set
    var calculationNumber by mutableIntStateOf(0)
        private set
    var totalCalculationCount by mutableIntStateOf(0)
        private set
    var wrongDialogCalculation by mutableStateOf<Calculation?>(null)
        private set
    var retryCalculation by mutableStateOf<Calculation?>(null)
        private set
    var retryPromptSequence by mutableIntStateOf(0)
        private set
    var showLeaveConfirmation by mutableStateOf(false)
        private set
    var correctFlashSequence by mutableIntStateOf(0)
        private set
    var errorCount by mutableIntStateOf(0)
        private set
    var settingsPin by mutableStateOf<String?>(null)
        private set
    var settingsPinLoaded by mutableStateOf(false)
        private set
    var multiplicationNumbers by mutableStateOf((1..12).toSet())
        private set
    var divisionNumbers by mutableStateOf((1..12).toSet())
        private set

    private var highestNumber = 10
    private var firstNumber = 1
    private var calculations = emptyList<Calculation>()
    private var calculationIndex = 0
    private var firstRoundLength = 0
    private val wrongSecondNumbers = mutableSetOf<Int>()
    private val configurationDatabase = ConfigurationDatabase(application)
    private var configurationLoadJob: Job? = null
    private var scoreSaveJob: Job? = null
    private var timerPreferenceLoadJob: Job? = null
    private var timerHistorySaveJob: Job? = null
    private var timerJob: Job? = null
    private var remainingTimerMillis = 30L * 60L * 1000L
    private var activeUntilElapsedMillis = 0L
    private var lastTimerTickElapsedMillis = 0L
    private var timedNumberOfCalculations = 0
    private var timedCorrectCalculations = 0

    init {
        viewModelScope.launch {
            val (pin, multiplication, division) = withContext(Dispatchers.IO) {
                Triple(
                    configurationDatabase.loadSettingsPin(),
                    configurationDatabase.loadAvailableNumbers(MathOperator.MULTIPLY),
                    configurationDatabase.loadAvailableNumbers(MathOperator.DIVIDE),
                )
            }
            settingsPin = pin
            multiplicationNumbers = multiplication
            divisionNumbers = division
            settingsPinLoaded = true
        }
        timerPreferenceLoadJob = viewModelScope.launch {
            val savedMinutes = withContext(Dispatchers.IO) {
                configurationDatabase.loadTimerMinutes()
            }
            if (savedMinutes != null) {
                timerMinutes = savedMinutes
                if (!timerIsArmed) {
                    remainingTimerMillis = savedMinutes * 60_000L
                    updateDisplayedTimerSeconds()
                }
            }
        }
    }

    fun chooseOperator(value: MathOperator) {
        operator = value
        applyDefaultConfiguration(value)
        scoresByFirstNumber = emptyMap()
        screen = AppScreen.SETUP
        loadConfiguration(value)
    }

    fun openSettings() {
        screen = AppScreen.SETTINGS
    }

    fun saveSettingsPin(pin: String) {
        settingsPin = pin
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveSettingsPin(pin)
        }
    }

    fun numberIsAvailable(operator: MathOperator, number: Int): Boolean =
        number in if (operator == MathOperator.MULTIPLY) multiplicationNumbers else divisionNumbers

    fun updateNumberAvailability(operator: MathOperator, number: Int, enabled: Boolean) {
        val updated = (if (operator == MathOperator.MULTIPLY) multiplicationNumbers else divisionNumbers)
            .toMutableSet()
            .apply { if (enabled) add(number) else remove(number) }
            .toSet()
        if (operator == MathOperator.MULTIPLY) multiplicationNumbers = updated else divisionNumbers = updated
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveAvailableNumbers(operator, updated)
        }
    }

    fun updateHighestNumber(value: String) {
        if (value.all(Char::isDigit)) highestNumberText = value
        highestNumberError = null
    }

    fun updateRandomFirstSecond(value: Boolean) {
        randomFirstSecond = value && operator == MathOperator.MULTIPLY
    }

    fun updateWithoutOneAndTen(value: Boolean) {
        withoutOneAndTen = value
        highestNumberError = null
    }

    fun updateAutoEnter(value: Boolean) {
        autoEnter = value
    }

    fun updateOrderedNumbers(value: Boolean) {
        orderedNumbers = value
    }

    fun configureTimer(minutes: Int) {
        require(minutes > 0)
        timerPreferenceLoadJob?.cancel()
        timerJob?.cancel()
        timerMinutes = minutes
        remainingTimerMillis = minutes * 60_000L
        updateDisplayedTimerSeconds()
        activeUntilElapsedMillis = 0L
        lastTimerTickElapsedMillis = SystemClock.elapsedRealtime()
        timerIsArmed = true
        showTimeUpDialog = false
        timedNumberOfCalculations = 0
        timedCorrectCalculations = 0
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveTimerMinutes(minutes)
        }
    }

    fun onAnswerDigitEntered() {
        if (!timerIsArmed || remainingTimerMillis <= 0L) return
        val now = SystemClock.elapsedRealtime()
        if (now < activeUntilElapsedMillis) return
        consumeActiveTimerTime(now)
        lastTimerTickElapsedMillis = now
        activeUntilElapsedMillis = now + ACTIVE_INPUT_TIMEOUT_SECONDS * 1000L
        ensureTimerJob()
    }

    fun acknowledgeTimeUp() {
        showTimeUpDialog = false
    }

    fun loadTimerHistory() {
        isTimerHistoryLoading = true
        viewModelScope.launch {
            timerHistorySaveJob?.join()
            timerHistory = withContext(Dispatchers.IO) {
                configurationDatabase.loadTimerHistory()
            }
            isTimerHistoryLoading = false
        }
    }

    fun pauseTimerForBackground() {
        pauseTimerActivity()
    }

    fun startPractice(chosenFirstNumber: Int) {
        if (isConfigurationLoading) return
        val parsedHighestNumber = highestNumberText.toIntOrNull()
        if (parsedHighestNumber == null || parsedHighestNumber !in 1..1000) {
            highestNumberError = "Enter a number from 1 to 1000"
            return
        }
        if (withoutOneAndTen && parsedHighestNumber < 3) {
            highestNumberError = "Choose at least 3 when excluding 1 and 10"
            return
        }

        highestNumber = parsedHighestNumber
        firstNumber = chosenFirstNumber
        calculationIndex = 0
        calculationNumber = 1
        errorCount = 0
        correctFlashSequence = 0
        wrongSecondNumbers.clear()
        wrongDialogCalculation = null
        retryCalculation = null
        showLeaveConfirmation = false
        saveConfiguration()
        val firstRoundNumbers = PracticeEngine.firstRoundNumbers(
            highestNumber = highestNumber,
            withoutOneAndTen = withoutOneAndTen,
            keepSetLength = forceSetLength,
            orderedNumbers = orderedNumbers,
        )
        firstRoundLength = firstRoundNumbers.size
        totalCalculationCount = firstRoundLength * 2
        calculations = PracticeEngine.calculations(
            operator = operator,
            firstNumber = firstNumber,
            secondNumbers = firstRoundNumbers,
            randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
        )
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun submitAnswer(answerText: String) {
        if (wrongDialogCalculation != null || retryCalculation != null) return
        val calculation = currentCalculation ?: return
        val answer = answerText.toIntOrNull() ?: return
        if (timerIsArmed) {
            timedNumberOfCalculations++
            if (answer == calculation.expectedAnswer) timedCorrectCalculations++
            renewTimerActivityWindow()
        }

        if (answer == calculation.expectedAnswer) {
            correctFlashSequence++
            advance()
        } else {
            errorCount++
            wrongSecondNumbers += calculation.secondNumber
            if (!forceSetLength && calculationIndex < firstRoundLength) {
                totalCalculationCount = firstRoundLength + highestNumber
            }
            wrongDialogCalculation = calculation
        }
    }

    fun acceptCorrectAnswer() {
        val calculation = wrongDialogCalculation ?: return
        wrongDialogCalculation = null
        retryCalculation = calculation
        retryPromptSequence++
    }

    fun submitRetryAnswer(answerText: String) {
        val calculation = retryCalculation ?: return
        val answer = answerText.toIntOrNull() ?: return
        if (timerIsArmed) renewTimerActivityWindow()
        if (answer == calculation.expectedAnswer) {
            retryCalculation = null
            advance()
        } else {
            retryCalculation = null
            wrongDialogCalculation = calculation
        }
    }

    fun goBack() {
        if (screen == AppScreen.PRACTICE) {
            showLeaveConfirmation = true
            return
        }
        when (screen) {
            AppScreen.OPERATOR -> Unit
            AppScreen.SETUP -> screen = AppScreen.OPERATOR
            AppScreen.PRACTICE, AppScreen.RESULTS -> screen = AppScreen.SETUP
            AppScreen.SETTINGS -> screen = AppScreen.OPERATOR
        }
    }

    fun confirmLeavePractice() {
        showLeaveConfirmation = false
        retryCalculation = null
        wrongDialogCalculation = null
        pauseTimerActivity()
        screen = AppScreen.SETUP
    }

    fun continuePractice() {
        showLeaveConfirmation = false
    }

    fun startOver() {
        screen = AppScreen.OPERATOR
    }

    fun returnToSetup() {
        screen = AppScreen.SETUP
        loadConfiguration(operator)
    }

    override fun onCleared() {
        timerJob?.cancel()
        configurationDatabase.close()
        super.onCleared()
    }

    private fun applyDefaultConfiguration(selectedOperator: MathOperator) {
        highestNumberText = "10"
        randomFirstSecond = selectedOperator == MathOperator.MULTIPLY
        withoutOneAndTen = true
        autoEnter = false
        orderedNumbers = false
        highestNumberError = null
    }

    private fun loadConfiguration(selectedOperator: MathOperator) {
        configurationLoadJob?.cancel()
        isConfigurationLoading = true
        configurationLoadJob = viewModelScope.launch {
            scoreSaveJob?.join()
            val (savedConfiguration, savedScores) = withContext(Dispatchers.IO) {
                configurationDatabase.load(selectedOperator) to
                    configurationDatabase.loadScores(selectedOperator)
            }
            if (operator == selectedOperator) {
                scoresByFirstNumber = savedScores
                if (savedConfiguration != null) {
                    highestNumberText = savedConfiguration.highestNumber.toString()
                    randomFirstSecond = selectedOperator == MathOperator.MULTIPLY &&
                        savedConfiguration.randomFirstSecond
                    withoutOneAndTen = savedConfiguration.withoutOneAndTen
                    autoEnter = savedConfiguration.autoEnter
                    orderedNumbers = savedConfiguration.orderedNumbers
                }
            }
            isConfigurationLoading = false
        }
    }

    private fun saveConfiguration() {
        val configuration = PracticeConfiguration(
            highestNumber = highestNumber,
            randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
            withoutOneAndTen = withoutOneAndTen,
            autoEnter = autoEnter,
            orderedNumbers = orderedNumbers,
        )
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.save(operator, configuration)
        }
    }

    private fun saveScore() {
        scoresByFirstNumber = scoresByFirstNumber + (firstNumber to errorCount)
        scoreSaveJob = viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveScore(operator, firstNumber, errorCount)
        }
    }

    private fun ensureTimerJob() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (timerIsArmed) {
                delay(TIMER_TICK_MILLIS)
                val now = SystemClock.elapsedRealtime()
                consumeActiveTimerTime(now)
                if (!timerIsArmed || now >= activeUntilElapsedMillis) break
            }
        }
    }

    private fun renewTimerActivityWindow() {
        if (!timerIsArmed || remainingTimerMillis <= 0L) return
        val now = SystemClock.elapsedRealtime()
        consumeActiveTimerTime(now)
        if (!timerIsArmed) return
        lastTimerTickElapsedMillis = now
        activeUntilElapsedMillis = now + ACTIVE_INPUT_TIMEOUT_SECONDS * 1000L
        ensureTimerJob()
    }

    private fun consumeActiveTimerTime(now: Long) {
        if (activeUntilElapsedMillis <= lastTimerTickElapsedMillis) return
        val countedUntil = minOf(now, activeUntilElapsedMillis)
        val elapsed = (countedUntil - lastTimerTickElapsedMillis).coerceAtLeast(0L)
        remainingTimerMillis = (remainingTimerMillis - elapsed).coerceAtLeast(0L)
        lastTimerTickElapsedMillis = now
        updateDisplayedTimerSeconds()
        if (remainingTimerMillis == 0L) {
            timerIsArmed = false
            activeUntilElapsedMillis = 0L
            showTimeUpDialog = true
            timerHistorySaveJob = viewModelScope.launch(Dispatchers.IO) {
                configurationDatabase.saveTimerCompletion(
                    finishedAtMillis = System.currentTimeMillis(),
                    durationMinutes = timerMinutes,
                    correctCalculations = timedCorrectCalculations,
                    numberOfCalculations = timedNumberOfCalculations,
                )
            }
        }
    }

    private fun pauseTimerActivity() {
        if (!timerIsArmed) return
        consumeActiveTimerTime(SystemClock.elapsedRealtime())
        activeUntilElapsedMillis = 0L
        timerJob?.cancel()
        timerJob = null
    }

    private fun updateDisplayedTimerSeconds() {
        remainingTimerSeconds = ((remainingTimerMillis + 999L) / 1000L).toInt()
    }

    private fun advance() {
        calculationIndex++

        if (calculationIndex == firstRoundLength) {
            calculations = calculations + PracticeEngine.calculations(
                operator = operator,
                firstNumber = firstNumber,
                secondNumbers = PracticeEngine.reviewRoundNumbers(
                    highestNumber = highestNumber,
                    wrongSecondNumbers = wrongSecondNumbers,
                    withoutOneAndTen = withoutOneAndTen,
                    previousSecondNumber = calculations.last().secondNumber,
                    keepSetLength = forceSetLength,
                    orderedNumbers = orderedNumbers,
                ),
                randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
            )
            totalCalculationCount = calculations.size
        }

        if (calculationIndex >= calculations.size) {
            currentCalculation = null
            pauseTimerActivity()
            saveScore()
            screen = AppScreen.RESULTS
            return
        }

        calculationNumber = calculationIndex + 1
        currentCalculation = calculations[calculationIndex]
    }

    private companion object {
        const val ACTIVE_INPUT_TIMEOUT_SECONDS = 30
        const val TIMER_TICK_MILLIS = 250L
    }
}
