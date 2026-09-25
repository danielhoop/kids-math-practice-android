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
import java.util.Locale

enum class AppScreen { OPERATOR, SETUP, PRACTICE, RESULTS, SETTINGS }

private const val ACTIVE_PRACTICE_CLOCK_TIMEOUT_SECONDS = 30

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    var screen by mutableStateOf(AppScreen.OPERATOR)
        private set
    var operator by mutableStateOf(MathOperator.MULTIPLY)
        private set
    var highestNumberText by mutableStateOf("10")
        private set
    var highestInputText by mutableStateOf("")
        private set
    var highestResultText by mutableStateOf("")
        private set
    var minimumResultText by mutableStateOf("0")
        private set
    var subtractionHighestInputText by mutableStateOf("")
        private set
    var subtractionLowestResultText by mutableStateOf("")
        private set
    var subtractionMinimumInputText by mutableStateOf("")
        private set
    var subtractionMaximumResultText by mutableStateOf("")
        private set
    var subtractionSettingsError by mutableStateOf<String?>(null)
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
    var stopwatchIsArmed by mutableStateOf(false)
        private set
    var elapsedStopwatchSeconds by mutableIntStateOf(0)
        private set
    val isPracticeClockActive: Boolean
        get() = timerIsArmed || stopwatchIsArmed
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
    var selectedLanguageTag by mutableStateOf(defaultLanguageTag())
        private set
    var multiplicationNumbers by mutableStateOf((1..12).toSet())
        private set
    var divisionNumbers by mutableStateOf((1..12).toSet())
        private set
    var mixedNumbers by mutableStateOf((1..12).toSet())
        private set
    var multiplicationShowHints by mutableStateOf(true)
        private set
    var divisionShowHints by mutableStateOf(true)
        private set

    private var highestNumber = 10
    private var firstNumber = 1
    private var wildcardPractice = false
    private var calculations = emptyList<Calculation>()
    private var calculationIndex = 0
    private var firstRoundLength = 0
    private val wrongSecondNumbers = mutableSetOf<Int>()
    private val configurationDatabase = ConfigurationDatabase(application)
    private var configurationLoadJob: Job? = null
    private var scoreSaveJob: Job? = null
    private var timerPreferenceLoadJob: Job? = null
    private var timerHistorySaveJob: Job? = null
    private var timerProgressSaveJob: Job? = null
    private var timerJob: Job? = null
    private var remainingTimerMillis = 30L * 60L * 1000L
    private var elapsedStopwatchMillis = 0L
    private var activeUntilElapsedMillis = 0L
    private var lastTimerTickElapsedMillis = 0L
    private var lastTimerProgressSaveElapsedMillis = 0L
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
            val (multiplicationHints, divisionHints) = withContext(Dispatchers.IO) {
                configurationDatabase.loadShowHints(MathOperator.MULTIPLY) to
                    configurationDatabase.loadShowHints(MathOperator.DIVIDE)
            }
            settingsPin = pin
            multiplicationNumbers = multiplication
            divisionNumbers = division
            mixedNumbers = withContext(Dispatchers.IO) {
                configurationDatabase.loadAvailableNumbers(MathOperator.MIXED)
            }
            multiplicationShowHints = multiplicationHints
            divisionShowHints = divisionHints
            settingsPinLoaded = true
        }
        viewModelScope.launch {
            val savedLanguage = withContext(Dispatchers.IO) { configurationDatabase.loadLanguage() }
            savedLanguage?.takeIf { it in SUPPORTED_LANGUAGE_TAGS }?.let {
                selectedLanguageTag = it
            }
        }
        timerPreferenceLoadJob = viewModelScope.launch {
            val (savedTimerProgress, savedStopwatchProgress) = withContext(Dispatchers.IO) {
                configurationDatabase.loadTimerProgress() to
                    configurationDatabase.loadStopwatchProgress()
            }
            if (savedTimerProgress != null) {
                timerMinutes = savedTimerProgress.minutes
                remainingTimerMillis = if (savedTimerProgress.isArmed) {
                    savedTimerProgress.remainingMillis
                } else {
                    savedTimerProgress.minutes * 60_000L
                }
                timerIsArmed = savedTimerProgress.isArmed && remainingTimerMillis > 0L
                updateDisplayedTimerSeconds()
            }
            if (!timerIsArmed && savedStopwatchProgress != null) {
                elapsedStopwatchMillis = savedStopwatchProgress.elapsedMillis
                stopwatchIsArmed = savedStopwatchProgress.isArmed
                updateDisplayedStopwatchSeconds()
            }
        }
    }

    fun chooseOperator(value: MathOperator) {
        operator = value
        applyDefaultConfiguration(value)
        scoresByFirstNumber = emptyMap()
        screen = AppScreen.SETUP
        when (value) {
            MathOperator.ADDITION -> loadAdditionConfiguration()
            MathOperator.SUBTRACTION -> loadSubtractionConfiguration()
            else -> loadConfiguration(value)
        }
    }

    fun updateHighestInput(value: String) {
        if (value.all(Char::isDigit)) highestInputText = value
        highestNumberError = null
    }

    fun updateHighestResult(value: String) {
        if (value.all(Char::isDigit)) highestResultText = value
        highestNumberError = null
    }

    fun updateMinimumResult(value: String) {
        if (value.all(Char::isDigit)) {
            minimumResultText = value
            saveAdditionConfiguration()
        }
    }

    fun updateSubtractionHighestInput(value: String) {
        if (value.all(Char::isDigit)) subtractionHighestInputText = value
    }

    fun updateSubtractionLowestResult(value: String) {
        if (value.all(Char::isDigit)) subtractionLowestResultText = value
    }

    fun updateSubtractionMinimumInput(value: String) {
        if (value.all(Char::isDigit)) {
            subtractionMinimumInputText = value
            subtractionSettingsError = validateSubtractionRestrictions()
            if (subtractionSettingsError == null) saveSubtractionConfiguration()
        }
    }

    fun updateSubtractionMaximumResult(value: String) {
        if (value.all(Char::isDigit)) {
            subtractionMaximumResultText = value
            subtractionSettingsError = validateSubtractionRestrictions()
            if (subtractionSettingsError == null) saveSubtractionConfiguration()
        }
    }

    private fun validateSubtractionRestrictions(): String? {
        val minimumInput = subtractionMinimumInputText.toIntOrNull()
        val maximumResult = subtractionMaximumResultText.toIntOrNull()
        return if (minimumInput != null && maximumResult != null && maximumResult >= minimumInput) {
            getApplication<Application>().getString(R.string.maximum_result_less_than_minimum_input)
        } else {
            null
        }
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

    fun updateLanguage(languageTag: String) {
        require(languageTag in SUPPORTED_LANGUAGE_TAGS)
        selectedLanguageTag = languageTag
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveLanguage(languageTag)
        }
    }

    fun numberIsAvailable(operator: MathOperator, number: Int): Boolean =
        number in when (operator) {
            MathOperator.ADDITION -> emptySet()
            MathOperator.SUBTRACTION -> emptySet()
            MathOperator.MULTIPLY -> multiplicationNumbers
            MathOperator.DIVIDE -> divisionNumbers
            MathOperator.MIXED -> mixedNumbers
        }

    private fun availableNumbers(operator: MathOperator): List<Int> = when (operator) {
        MathOperator.ADDITION -> emptyList()
        MathOperator.SUBTRACTION -> emptyList()
        MathOperator.MULTIPLY -> multiplicationNumbers.toList()
        MathOperator.DIVIDE -> divisionNumbers.toList()
        MathOperator.MIXED -> mixedNumbers.toList()
    }

    fun showHints(operator: MathOperator): Boolean =
        if (operator == MathOperator.MULTIPLY) multiplicationShowHints else divisionShowHints

    fun showHintsFor(calculation: Calculation): Boolean = showHints(calculation.calculationOperator)

    fun updateShowHints(operator: MathOperator, show: Boolean) {
        if (operator == MathOperator.MULTIPLY) multiplicationShowHints = show else divisionShowHints = show
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveShowHints(operator, show)
        }
    }

    fun updateNumberAvailability(operator: MathOperator, number: Int, enabled: Boolean) {
        val updated = (when (operator) {
            MathOperator.ADDITION -> emptySet()
            MathOperator.SUBTRACTION -> emptySet()
            MathOperator.MULTIPLY -> multiplicationNumbers
            MathOperator.DIVIDE -> divisionNumbers
            MathOperator.MIXED -> mixedNumbers
        })
            .toMutableSet()
            .apply { if (enabled) add(number) else remove(number) }
            .toSet()
        when (operator) {
            MathOperator.ADDITION -> Unit
            MathOperator.SUBTRACTION -> Unit
            MathOperator.MULTIPLY -> multiplicationNumbers = updated
            MathOperator.DIVIDE -> divisionNumbers = updated
            MathOperator.MIXED -> mixedNumbers = updated
        }
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveAvailableNumbers(operator, updated)
        }
    }

    fun updateHighestNumber(value: String) {
        if (value.all(Char::isDigit)) highestNumberText = value
        highestNumberError = null
    }

    fun updateRandomFirstSecond(value: Boolean) {
        randomFirstSecond = value && operator != MathOperator.DIVIDE
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

    fun wildcardScore(): Int? = scoresByFirstNumber[WILDCARD_SCORE_FIRST_NUMBER]

    fun configureTimer(minutes: Int) {
        require(minutes > 0)
        timerPreferenceLoadJob?.cancel()
        timerJob?.cancel()
        stopwatchIsArmed = false
        saveStopwatchProgress()
        timerMinutes = minutes
        remainingTimerMillis = minutes * 60_000L
        updateDisplayedTimerSeconds()
        activeUntilElapsedMillis = 0L
        lastTimerTickElapsedMillis = SystemClock.elapsedRealtime()
        lastTimerProgressSaveElapsedMillis = lastTimerTickElapsedMillis
        timerIsArmed = true
        showTimeUpDialog = false
        timedNumberOfCalculations = 0
        timedCorrectCalculations = 0
        saveTimerProgress()
    }

    fun configureStopwatch() {
        timerPreferenceLoadJob?.cancel()
        timerJob?.cancel()
        timerIsArmed = false
        activeUntilElapsedMillis = 0L
        saveTimerProgress()
        elapsedStopwatchMillis = 0L
        updateDisplayedStopwatchSeconds()
        lastTimerTickElapsedMillis = SystemClock.elapsedRealtime()
        lastTimerProgressSaveElapsedMillis = lastTimerTickElapsedMillis
        stopwatchIsArmed = true
        showTimeUpDialog = false
        timedNumberOfCalculations = 0
        timedCorrectCalculations = 0
        saveStopwatchProgress()
    }

    fun onAnswerDigitEntered() {
        if (!isPracticeClockActive) return
        val now = SystemClock.elapsedRealtime()
        if (now < activeUntilElapsedMillis) return
        consumeActiveClockTime(now)
        lastTimerTickElapsedMillis = now
        activeUntilElapsedMillis = now + ACTIVE_PRACTICE_CLOCK_TIMEOUT_SECONDS * 1000L
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
        pausePracticeClockActivity()
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
        wildcardPractice = false
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
        if (operator == MathOperator.MIXED) {
            calculations = PracticeEngine.mixedFirstRoundCalculations(
                firstNumber = firstNumber,
                highestNumber = highestNumber,
                withoutOneAndTen = withoutOneAndTen,
                randomFirstSecond = randomFirstSecond,
            )
            firstRoundLength = calculations.size
            totalCalculationCount = firstRoundLength * 2
            currentCalculation = calculations.first()
            screen = AppScreen.PRACTICE
            return
        }
        firstRoundLength = firstRoundNumbers.size
        totalCalculationCount = firstRoundLength * 2
        calculations = PracticeEngine.calculations(
            operator = operator,
            firstNumber = firstNumber,
            secondNumbers = firstRoundNumbers,
            randomFirstSecond = operator != MathOperator.DIVIDE && randomFirstSecond,
        )
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun startWildcardPractice() {
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
        val baseNumbers = wildcardBaseNumbers(parsedHighestNumber)
        val secondNumbers = (1..parsedHighestNumber)
            .filterNot { withoutOneAndTen && (it == 1 || it == 10) }
        if (baseNumbers.isEmpty() || secondNumbers.isEmpty()) {
            highestNumberError = "Enable at least one available number for this mode"
            return
        }

        highestNumber = parsedHighestNumber
        firstNumber = 0
        wildcardPractice = true
        calculationIndex = 0
        calculationNumber = 1
        errorCount = 0
        correctFlashSequence = 0
        wrongSecondNumbers.clear()
        wrongDialogCalculation = null
        retryCalculation = null
        showLeaveConfirmation = false
        saveConfiguration()
        calculations = PracticeEngine.wildcardFirstRoundCalculations(
            operator = operator,
            baseNumbers = baseNumbers,
            secondNumbers = secondNumbers,
            randomFirstSecond = randomFirstSecond,
        )
        if (calculations.isEmpty()) {
            highestNumberError = "No calculations are available with these settings"
            return
        }
        firstRoundLength = calculations.size
        totalCalculationCount = firstRoundLength * 2
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun startAdditionPractice() {
        if (isConfigurationLoading) return
        val input = highestInputText.toIntOrNull()
        val result = highestResultText.toIntOrNull()
        val minimumResult = minimumResultText.toIntOrNull() ?: 0
        if ((input == null) == (result == null)) {
            highestNumberError = "Choose either Highest input or Highest result, but not both"
            return
        }
        if (input != null && input !in 1..1000) {
            highestNumberError = "Highest input must be from 1 to 1000"
            return
        }
        if (result != null && result < 1) {
            highestNumberError = "Highest result must be at least 1"
            return
        }
        if (result != null && result < minimumResult) {
            highestNumberError = "Highest result must be at least $minimumResult"
            return
        }
        highestNumberError = null
        highestInputText = input?.toString() ?: ""
        highestResultText = result?.toString() ?: ""
        saveAdditionConfiguration()
        firstNumber = 0
        wildcardPractice = false
        calculationIndex = 0
        calculationNumber = 1
        errorCount = 0
        correctFlashSequence = 0
        wrongSecondNumbers.clear()
        wrongDialogCalculation = null
        retryCalculation = null
        showLeaveConfirmation = false
        calculations = PracticeEngine.additionCalculations(input, result, minimumResult = minimumResult)
        if (calculations.isEmpty()) {
            highestNumberError = "No calculations are available with these settings"
            return
        }
        firstRoundLength = calculations.size
        totalCalculationCount = calculations.size
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun startSubtractionPractice() {
        if (isConfigurationLoading) return
        val highestInput = subtractionHighestInputText.toIntOrNull()
        val lowestResult = subtractionLowestResultText.toIntOrNull()
        val minimumInput = subtractionMinimumInputText.toIntOrNull()
        val maximumResult = subtractionMaximumResultText.toIntOrNull()
        if (highestInput == null || lowestResult == null) {
            highestNumberError = "Enter Highest input and Lowest result"
            return
        }
        if (lowestResult >= highestInput) {
            highestNumberError = "Lowest result must be smaller than Highest input"
            return
        }
        validateSubtractionRestrictions()?.let {
            subtractionSettingsError = it
            return
        }
        if (minimumInput != null && highestInput < minimumInput) {
            highestNumberError = "Highest input must be at least $minimumInput"
            return
        }
        if (maximumResult != null && lowestResult > maximumResult) {
            highestNumberError = "Lowest result must be at most $maximumResult"
            return
        }
        if (highestInput !in 1..1000 || lowestResult < 0) {
            highestNumberError = "Enter values from 0 to 1000"
            return
        }
        highestNumberError = null
        saveSubtractionConfiguration()
        firstNumber = 0
        wildcardPractice = false
        calculationIndex = 0
        calculationNumber = 1
        errorCount = 0
        correctFlashSequence = 0
        wrongSecondNumbers.clear()
        wrongDialogCalculation = null
        retryCalculation = null
        showLeaveConfirmation = false
        calculations = PracticeEngine.subtractionCalculations(highestInput, lowestResult)
        if (calculations.isEmpty()) {
            highestNumberError = "No calculations are available with these settings"
            return
        }
        firstRoundLength = calculations.size
        totalCalculationCount = calculations.size
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun submitAnswer(answerText: String) {
        if (wrongDialogCalculation != null || retryCalculation != null) return
        val calculation = currentCalculation ?: return
        val answer = answerText.toIntOrNull() ?: return
        if (isPracticeClockActive) {
            timedNumberOfCalculations++
            if (answer == calculation.expectedAnswer) timedCorrectCalculations++
            renewPracticeClockActivityWindow()
        }

        if (answer == calculation.expectedAnswer) {
            correctFlashSequence++
            advance()
        } else {
            errorCount++
            wrongSecondNumbers += calculation.secondNumber
            if (operator != MathOperator.ADDITION && operator != MathOperator.SUBTRACTION &&
                !forceSetLength && calculationIndex < firstRoundLength
            ) {
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
        if (isPracticeClockActive) renewPracticeClockActivityWindow()
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
        pausePracticeClockActivity()
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
        when (operator) {
            MathOperator.ADDITION -> loadAdditionConfiguration()
            MathOperator.SUBTRACTION -> loadSubtractionConfiguration()
            else -> loadConfiguration(operator)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        configurationDatabase.close()
        super.onCleared()
    }

    private fun applyDefaultConfiguration(selectedOperator: MathOperator) {
        highestNumberText = "10"
        highestInputText = ""
        highestResultText = ""
        minimumResultText = "0"
        subtractionHighestInputText = ""
        subtractionLowestResultText = ""
        subtractionMinimumInputText = ""
        subtractionMaximumResultText = ""
        randomFirstSecond = selectedOperator != MathOperator.DIVIDE
        withoutOneAndTen = true
        autoEnter = false
        orderedNumbers = false
        highestNumberError = null
    }

    private fun loadAdditionConfiguration() {
        configurationLoadJob?.cancel()
        isConfigurationLoading = true
        configurationLoadJob = viewModelScope.launch {
            val configuration = withContext(Dispatchers.IO) {
                configurationDatabase.loadAdditionConfiguration()
            }
            if (operator == MathOperator.ADDITION && configuration != null) {
                highestInputText = configuration.highestInput?.toString() ?: ""
                highestResultText = configuration.highestResult?.toString() ?: ""
                autoEnter = configuration.autoEnter
                minimumResultText = configuration.minimumResult.toString()
            }
            isConfigurationLoading = false
        }
    }

    private fun loadSubtractionConfiguration() {
        configurationLoadJob?.cancel()
        isConfigurationLoading = true
        configurationLoadJob = viewModelScope.launch {
            val configuration = withContext(Dispatchers.IO) {
                configurationDatabase.loadSubtractionConfiguration()
            }
            if (operator == MathOperator.SUBTRACTION && configuration != null) {
                subtractionHighestInputText = configuration.highestInput?.toString() ?: ""
                subtractionLowestResultText = configuration.lowestResult?.toString() ?: ""
                autoEnter = configuration.autoEnter
                subtractionMinimumInputText = configuration.minimumInput?.toString() ?: ""
                subtractionMaximumResultText = configuration.maximumResult?.toString() ?: ""
                subtractionSettingsError = validateSubtractionRestrictions()
            }
            isConfigurationLoading = false
        }
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
                    randomFirstSecond = selectedOperator != MathOperator.DIVIDE &&
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
            randomFirstSecond = operator != MathOperator.DIVIDE && randomFirstSecond,
            withoutOneAndTen = withoutOneAndTen,
            autoEnter = autoEnter,
            orderedNumbers = orderedNumbers,
        )
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.save(operator, configuration)
        }
    }

    private fun saveAdditionConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveAdditionConfiguration(
                AdditionConfiguration(
                    highestInput = highestInputText.toIntOrNull(),
                    highestResult = highestResultText.toIntOrNull(),
                    autoEnter = autoEnter,
                    minimumResult = minimumResultText.toIntOrNull() ?: 0,
                ),
            )
        }
    }

    private fun saveSubtractionConfiguration() {
        viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveSubtractionConfiguration(
                SubtractionConfiguration(
                    highestInput = subtractionHighestInputText.toIntOrNull(),
                    lowestResult = subtractionLowestResultText.toIntOrNull(),
                    autoEnter = autoEnter,
                    minimumInput = subtractionMinimumInputText.toIntOrNull(),
                    maximumResult = subtractionMaximumResultText.toIntOrNull(),
                ),
            )
        }
    }

    private fun saveScore() {
        if (operator == MathOperator.ADDITION || operator == MathOperator.SUBTRACTION) return
        val scoreFirstNumber = if (wildcardPractice) WILDCARD_SCORE_FIRST_NUMBER else firstNumber
        scoresByFirstNumber = scoresByFirstNumber + (scoreFirstNumber to errorCount)
        scoreSaveJob = viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveScore(operator, scoreFirstNumber, errorCount)
        }
    }

    private fun ensureTimerJob() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (isPracticeClockActive) {
                delay(TIMER_TICK_MILLIS)
                val now = SystemClock.elapsedRealtime()
                consumeActiveClockTime(now)
                if (!isPracticeClockActive || now >= activeUntilElapsedMillis) break
            }
        }
    }

    private fun renewPracticeClockActivityWindow() {
        if (!isPracticeClockActive) return
        val now = SystemClock.elapsedRealtime()
        consumeActiveClockTime(now)
        if (!isPracticeClockActive) return
        lastTimerTickElapsedMillis = now
        activeUntilElapsedMillis = now + ACTIVE_PRACTICE_CLOCK_TIMEOUT_SECONDS * 1000L
        ensureTimerJob()
    }

    private fun consumeActiveClockTime(now: Long) {
        if (activeUntilElapsedMillis <= lastTimerTickElapsedMillis) return
        val countedUntil = minOf(now, activeUntilElapsedMillis)
        val elapsed = (countedUntil - lastTimerTickElapsedMillis).coerceAtLeast(0L)
        lastTimerTickElapsedMillis = now
        if (timerIsArmed) {
            remainingTimerMillis = (remainingTimerMillis - elapsed).coerceAtLeast(0L)
            updateDisplayedTimerSeconds()
        } else if (stopwatchIsArmed) {
            elapsedStopwatchMillis += elapsed
            updateDisplayedStopwatchSeconds()
        }
        if (timerIsArmed && remainingTimerMillis == 0L) {
            timerIsArmed = false
            activeUntilElapsedMillis = 0L
            saveTimerProgress()
            showTimeUpDialog = true
            timerHistorySaveJob = viewModelScope.launch(Dispatchers.IO) {
                configurationDatabase.saveTimerCompletion(
                    finishedAtMillis = System.currentTimeMillis(),
                    durationMinutes = timerMinutes,
                    correctCalculations = timedCorrectCalculations,
                    numberOfCalculations = timedNumberOfCalculations,
                )
            }
        } else if (isPracticeClockActive &&
            now - lastTimerProgressSaveElapsedMillis >= TIMER_PROGRESS_SAVE_MILLIS
        ) {
            if (timerIsArmed) saveTimerProgress() else saveStopwatchProgress()
            lastTimerProgressSaveElapsedMillis = now
        }
    }

    private fun pausePracticeClockActivity() {
        if (!isPracticeClockActive) return
        consumeActiveClockTime(SystemClock.elapsedRealtime())
        activeUntilElapsedMillis = 0L
        timerJob?.cancel()
        timerJob = null
        if (timerIsArmed) saveTimerProgress() else saveStopwatchProgress()
    }

    private fun saveTimerProgress() {
        val minutes = timerMinutes
        val remainingMillis = remainingTimerMillis
        val isArmed = timerIsArmed
        timerProgressSaveJob = viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveTimerProgress(minutes, remainingMillis, isArmed)
        }
    }

    private fun saveStopwatchProgress() {
        val elapsedMillis = elapsedStopwatchMillis
        val isArmed = stopwatchIsArmed
        timerProgressSaveJob = viewModelScope.launch(Dispatchers.IO) {
            configurationDatabase.saveStopwatchProgress(elapsedMillis, isArmed)
        }
    }

    private fun updateDisplayedTimerSeconds() {
        remainingTimerSeconds = ((remainingTimerMillis + 999L) / 1000L).toInt()
    }

    private fun updateDisplayedStopwatchSeconds() {
        elapsedStopwatchSeconds = (elapsedStopwatchMillis / 1000L).toInt()
    }

    private fun wildcardBaseNumbers(highestAllowedNumber: Int): List<Int> =
        availableNumbers(operator)
            .filter { it <= highestAllowedNumber }
            .filterNot { withoutOneAndTen && (it == 1 || it == 10) }

    private fun advance() {
        calculationIndex++

        if (calculationIndex == firstRoundLength &&
            operator != MathOperator.ADDITION && operator != MathOperator.SUBTRACTION
        ) {
            val reviewNumbers = if (wildcardPractice && wrongSecondNumbers.isEmpty()) {
                (1..highestNumber).filterNot { withoutOneAndTen && (it == 1 || it == 10) }
            } else {
                PracticeEngine.reviewRoundNumbers(
                    highestNumber = highestNumber,
                    wrongSecondNumbers = wrongSecondNumbers,
                    withoutOneAndTen = withoutOneAndTen,
                    previousSecondNumber = calculations.last().secondNumber,
                    keepSetLength = forceSetLength,
                    orderedNumbers = orderedNumbers,
                    roundSizeOverride = if (operator == MathOperator.MIXED || wildcardPractice) {
                        firstRoundLength
                    } else {
                        null
                    },
                )
            }
            val reviewCalculations = if (wildcardPractice) {
                PracticeEngine.wildcardFirstRoundCalculations(
                    operator = operator,
                    baseNumbers = wildcardBaseNumbers(highestNumber),
                    secondNumbers = reviewNumbers,
                    randomFirstSecond = randomFirstSecond,
                    roundSize = firstRoundLength,
                    excludedCalculations = if (wrongSecondNumbers.isEmpty()) {
                        calculations.map { calculation ->
                            val operation = calculation.calculationOperator
                            if (operation == MathOperator.MULTIPLY) {
                                Triple(
                                    operation,
                                    minOf(calculation.firstNumber, calculation.secondNumber),
                                    maxOf(calculation.firstNumber, calculation.secondNumber),
                                )
                            } else {
                                Triple(operation, calculation.firstNumber, calculation.secondNumber)
                            }
                        }.toSet()
                    } else {
                        emptySet()
                    },
                )
            } else {
                PracticeEngine.calculations(
                    operator = operator,
                    firstNumber = firstNumber,
                    secondNumbers = reviewNumbers,
                    randomFirstSecond = operator != MathOperator.DIVIDE && randomFirstSecond,
                )
            }
            calculations = calculations + reviewCalculations
            totalCalculationCount = calculations.size
        }

        if (calculationIndex >= calculations.size) {
            currentCalculation = null
            pausePracticeClockActivity()
            saveScore()
            screen = AppScreen.RESULTS
            return
        }

        calculationNumber = calculationIndex + 1
        currentCalculation = calculations[calculationIndex]
    }

    private companion object {
        val SUPPORTED_LANGUAGE_TAGS = setOf("en-US", "de-CH", "de-DE", "de-AT", "es-ES", "fr-FR", "it-IT", "nl-NL")
        const val WILDCARD_SCORE_FIRST_NUMBER = 0
        const val TIMER_TICK_MILLIS = 250L
        const val TIMER_PROGRESS_SAVE_MILLIS = 10_000L
    }
}

private fun defaultLanguageTag(): String = when {
    Locale.getDefault().language == "es" -> "es-ES"
    Locale.getDefault().language == "fr" -> "fr-FR"
    Locale.getDefault().language == "it" -> "it-IT"
    Locale.getDefault().language == "nl" -> "nl-NL"
    Locale.getDefault().language != "de" -> "en-US"
    Locale.getDefault().country == "CH" -> "de-CH"
    Locale.getDefault().country == "AT" -> "de-AT"
    else -> "de-DE"
}
