package com.danielhoop.timestables

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { OPERATOR, SETUP, PRACTICE, RESULTS }

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
    var highestNumberError by mutableStateOf<String?>(null)
        private set
    var isConfigurationLoading by mutableStateOf(false)
        private set
    var scoresByFirstNumber by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set
    var currentCalculation by mutableStateOf<Calculation?>(null)
        private set
    var calculationNumber by mutableIntStateOf(0)
        private set
    var wrongDialogCalculation by mutableStateOf<Calculation?>(null)
        private set
    var correctFlashSequence by mutableIntStateOf(0)
        private set
    var errorCount by mutableIntStateOf(0)
        private set

    private var highestNumber = 10
    private var firstNumber = 1
    private var calculations = emptyList<Calculation>()
    private var calculationIndex = 0
    private val wrongSecondNumbers = mutableSetOf<Int>()
    private val configurationDatabase = ConfigurationDatabase(application)
    private var configurationLoadJob: Job? = null
    private var scoreSaveJob: Job? = null

    fun chooseOperator(value: MathOperator) {
        operator = value
        applyDefaultConfiguration(value)
        scoresByFirstNumber = emptyMap()
        screen = AppScreen.SETUP
        loadConfiguration(value)
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
        saveConfiguration()
        calculations = PracticeEngine.calculations(
            operator = operator,
            firstNumber = firstNumber,
            secondNumbers = PracticeEngine.firstRoundNumbers(
                highestNumber = highestNumber,
                withoutOneAndTen = withoutOneAndTen,
            ),
            randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
        )
        currentCalculation = calculations.first()
        screen = AppScreen.PRACTICE
    }

    fun submitAnswer(answerText: String) {
        if (wrongDialogCalculation != null) return
        val calculation = currentCalculation ?: return
        val answer = answerText.toIntOrNull() ?: return

        if (answer == calculation.expectedAnswer) {
            correctFlashSequence++
            advance()
        } else {
            errorCount++
            wrongSecondNumbers += calculation.secondNumber
            wrongDialogCalculation = calculation
        }
    }

    fun acceptCorrectAnswer() {
        if (wrongDialogCalculation == null) return
        wrongDialogCalculation = null
        advance()
    }

    fun goBack() {
        when (screen) {
            AppScreen.OPERATOR -> Unit
            AppScreen.SETUP -> screen = AppScreen.OPERATOR
            AppScreen.PRACTICE, AppScreen.RESULTS -> screen = AppScreen.SETUP
        }
    }

    fun startOver() {
        screen = AppScreen.OPERATOR
    }

    fun returnToSetup() {
        screen = AppScreen.SETUP
        loadConfiguration(operator)
    }

    override fun onCleared() {
        configurationDatabase.close()
        super.onCleared()
    }

    private fun applyDefaultConfiguration(selectedOperator: MathOperator) {
        highestNumberText = "10"
        randomFirstSecond = selectedOperator == MathOperator.MULTIPLY
        withoutOneAndTen = true
        autoEnter = false
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

    private fun advance() {
        calculationIndex++

        if (calculationIndex == highestNumber) {
            calculations = calculations + PracticeEngine.calculations(
                operator = operator,
                firstNumber = firstNumber,
                secondNumbers = PracticeEngine.reviewRoundNumbers(
                    highestNumber = highestNumber,
                    wrongSecondNumbers = wrongSecondNumbers,
                    withoutOneAndTen = withoutOneAndTen,
                ),
                randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
            )
        }

        if (calculationIndex >= highestNumber * 2) {
            currentCalculation = null
            saveScore()
            screen = AppScreen.RESULTS
            return
        }

        calculationNumber = calculationIndex + 1
        currentCalculation = calculations[calculationIndex]
    }
}
