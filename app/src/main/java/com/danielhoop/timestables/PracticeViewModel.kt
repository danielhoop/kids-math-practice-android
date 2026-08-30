package com.danielhoop.timestables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class AppScreen { OPERATOR, SETUP, PRACTICE, RESULTS }

class PracticeViewModel : ViewModel() {
    var screen by mutableStateOf(AppScreen.OPERATOR)
        private set
    var operator by mutableStateOf(MathOperator.MULTIPLY)
        private set
    var highestNumberText by mutableStateOf("10")
        private set
    var randomFirstSecond by mutableStateOf(false)
        private set
    var highestNumberError by mutableStateOf<String?>(null)
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

    fun chooseOperator(value: MathOperator) {
        operator = value
        if (value == MathOperator.DIVIDE) {
            randomFirstSecond = false
        }
        screen = AppScreen.SETUP
    }

    fun updateHighestNumber(value: String) {
        if (value.all(Char::isDigit)) highestNumberText = value
        highestNumberError = null
    }

    fun updateRandomFirstSecond(value: Boolean) {
        randomFirstSecond = value && operator == MathOperator.MULTIPLY
    }

    fun startPractice(chosenFirstNumber: Int) {
        val parsedHighestNumber = highestNumberText.toIntOrNull()
        if (parsedHighestNumber == null || parsedHighestNumber !in 1..1000) {
            highestNumberError = "Enter a number from 1 to 1000"
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
        calculations = PracticeEngine.calculations(
            operator = operator,
            firstNumber = firstNumber,
            secondNumbers = PracticeEngine.firstRoundNumbers(highestNumber),
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

    private fun advance() {
        calculationIndex++

        if (calculationIndex == highestNumber) {
            calculations = calculations + PracticeEngine.calculations(
                operator = operator,
                firstNumber = firstNumber,
                secondNumbers = PracticeEngine.reviewRoundNumbers(
                    highestNumber = highestNumber,
                    wrongSecondNumbers = wrongSecondNumbers,
                ),
                randomFirstSecond = operator == MathOperator.MULTIPLY && randomFirstSecond,
            )
        }

        if (calculationIndex >= highestNumber * 2) {
            currentCalculation = null
            screen = AppScreen.RESULTS
            return
        }

        calculationNumber = calculationIndex + 1
        currentCalculation = calculations[calculationIndex]
    }
}
