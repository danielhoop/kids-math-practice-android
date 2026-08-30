package com.danielhoop.timestables

import kotlin.math.min
import kotlin.random.Random

const val REPEAT_WRONG_NUMBER = 3

enum class MathOperator(val symbol: String) {
    MULTIPLY("·"),
    DIVIDE(":"),
}

data class Calculation(
    val operator: MathOperator,
    val firstNumber: Int,
    val secondNumber: Int,
    /** Controls visual order for multiplication. It is always false for division. */
    val swapRoles: Boolean,
) {
    val expectedAnswer: Int
        get() = when (operator) {
            MathOperator.MULTIPLY -> firstNumber * secondNumber
            MathOperator.DIVIDE -> secondNumber
        }

    val expression: String
        get() = when (operator) {
            MathOperator.MULTIPLY -> {
                val left = if (swapRoles) firstNumber else secondNumber
                val right = if (swapRoles) secondNumber else firstNumber
                "$left ${operator.symbol} $right"
            }

            MathOperator.DIVIDE -> {
                val product = firstNumber * secondNumber
                "$product ${operator.symbol} $firstNumber"
            }
        }
}

/** Pure practice-row generation, kept free of Android APIs so it is easy to test. */
object PracticeEngine {
    fun firstRoundNumbers(highestNumber: Int, random: Random = Random.Default): List<Int> {
        require(highestNumber > 0)
        return (1..highestNumber).shuffled(random)
    }

    fun reviewRoundNumbers(
        highestNumber: Int,
        wrongSecondNumbers: Set<Int>,
        random: Random = Random.Default,
        repeat: Int = REPEAT_WRONG_NUMBER,
    ): List<Int> {
        require(highestNumber > 0)
        require(repeat > 0)

        val allNumbers = (1..highestNumber).toList()
        val validWrongNumbers = wrongSecondNumbers.filter { it in 1..highestNumber }
        val maximumImportant = highestNumber / repeat
        val importantNumbers = validWrongNumbers
            .shuffled(random)
            .take(min(validWrongNumbers.size, maximumImportant))

        val repeatedImportant = importantNumbers.flatMap { number -> List(repeat) { number } }
        val remainingCount = highestNumber - repeatedImportant.size
        val otherNumbers = allNumbers
            .filterNot { it in importantNumbers }
            .shuffled(random)
            .take(remainingCount)

        return (repeatedImportant + otherNumbers).shuffled(random)
    }

    fun calculations(
        operator: MathOperator,
        firstNumber: Int,
        secondNumbers: List<Int>,
        randomFirstSecond: Boolean,
        random: Random = Random.Default,
    ): List<Calculation> = secondNumbers.map { secondNumber ->
        Calculation(
            operator = operator,
            firstNumber = firstNumber,
            secondNumber = secondNumber,
            swapRoles = operator == MathOperator.MULTIPLY &&
                randomFirstSecond && random.nextBoolean(),
        )
    }
}
