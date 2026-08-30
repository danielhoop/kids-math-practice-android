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
    fun firstRoundNumbers(
        highestNumber: Int,
        withoutOneAndTen: Boolean = false,
        random: Random = Random.Default,
    ): List<Int> {
        require(highestNumber > 0)
        val allowedNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allowedNumbers.isNotEmpty())
        require(highestNumber == 1 || allowedNumbers.size >= 2)
        return arrangeWithoutAdjacentDuplicates(
            numbers = fillRound(
            requiredNumbers = allowedNumbers.shuffled(random),
            fillNumbers = allowedNumbers,
            size = highestNumber,
            random = random,
            ),
            random = random,
        )
    }

    fun reviewRoundNumbers(
        highestNumber: Int,
        wrongSecondNumbers: Set<Int>,
        withoutOneAndTen: Boolean = false,
        random: Random = Random.Default,
        repeat: Int = REPEAT_WRONG_NUMBER,
    ): List<Int> {
        require(highestNumber > 0)
        require(repeat > 0)

        val allNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allNumbers.isNotEmpty())
        require(highestNumber == 1 || allNumbers.size >= 2)
        val validWrongNumbers = wrongSecondNumbers.filter { it in allNumbers }
        // A number can occupy at most every other position without touching itself.
        val safeRepeat = min(repeat, (highestNumber + 1) / 2)
        val maximumImportant = highestNumber / safeRepeat
        val importantNumbers = validWrongNumbers
            .shuffled(random)
            .take(min(validWrongNumbers.size, maximumImportant))

        val repeatedImportant = importantNumbers.flatMap { number -> List(safeRepeat) { number } }
        val remainingCount = highestNumber - repeatedImportant.size
        val otherNumberPool = allNumbers
            .filterNot { it in importantNumbers }
            .ifEmpty { allNumbers }
        val otherNumbers = fillRound(
            requiredNumbers = otherNumberPool.shuffled(random),
            fillNumbers = otherNumberPool,
            size = remainingCount,
            random = random,
        )

        return arrangeWithoutAdjacentDuplicates(repeatedImportant + otherNumbers, random)
    }

    private fun allowedNumbers(highestNumber: Int, withoutOneAndTen: Boolean): List<Int> =
        (1..highestNumber).filterNot { withoutOneAndTen && (it == 1 || it == 10) }

    private fun fillRound(
        requiredNumbers: List<Int>,
        fillNumbers: List<Int>,
        size: Int,
        random: Random,
    ): List<Int> {
        if (size == 0) return emptyList()
        require(fillNumbers.isNotEmpty())
        val result = requiredNumbers.take(size).toMutableList()
        while (result.size < size) {
            fillNumbers.shuffled(random).forEach { number ->
                if (result.size < size) result += number
            }
        }
        return result
    }

    private fun arrangeWithoutAdjacentDuplicates(
        numbers: List<Int>,
        random: Random,
    ): List<Int> {
        val remaining = numbers.groupingBy { it }.eachCount().toMutableMap()
        require((remaining.values.maxOrNull() ?: 0) <= (numbers.size + 1) / 2)
        val arranged = ArrayList<Int>(numbers.size)

        while (remaining.isNotEmpty()) {
            val candidates = remaining.filterKeys { it != arranged.lastOrNull() }
            require(candidates.isNotEmpty())
            val highestRemainingCount = candidates.maxOf { it.value }
            val nextNumber = candidates
                .filterValues { it == highestRemainingCount }
                .keys
                .toList()
                .random(random)
            arranged += nextNumber
            val newCount = remaining.getValue(nextNumber) - 1
            if (newCount == 0) remaining.remove(nextNumber) else remaining[nextNumber] = newCount
        }

        return arranged
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
