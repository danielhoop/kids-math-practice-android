package com.danielhoop.timestables

import kotlin.math.min
import kotlin.random.Random

const val REPEAT_WRONG_NUMBER = 3
const val forceSetLength = false

enum class MathOperator(val symbol: String) {
    MULTIPLY("×"),
    DIVIDE("÷"),
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
        keepSetLength: Boolean = forceSetLength,
        random: Random = Random.Default,
    ): List<Int> {
        require(highestNumber > 0)
        val allowedNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allowedNumbers.isNotEmpty())
        require(highestNumber == 1 || allowedNumbers.size >= 2)
        val roundSize = if (keepSetLength) highestNumber else allowedNumbers.size
        return arrangeWithoutAdjacentDuplicates(
            numbers = fillRound(
                requiredNumbers = allowedNumbers.shuffled(random),
                fillNumbers = allowedNumbers,
                size = roundSize,
                random = random,
            ),
            random = random,
        )
    }

    fun reviewRoundNumbers(
        highestNumber: Int,
        wrongSecondNumbers: Set<Int>,
        withoutOneAndTen: Boolean = false,
        previousSecondNumber: Int? = null,
        keepSetLength: Boolean = forceSetLength,
        random: Random = Random.Default,
        repeat: Int = REPEAT_WRONG_NUMBER,
    ): List<Int> {
        require(highestNumber > 0)
        require(repeat > 0)

        val allNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allNumbers.isNotEmpty())
        require(highestNumber == 1 || allNumbers.size >= 2)
        val validWrongNumbers = wrongSecondNumbers.filter { it in allNumbers }
        val roundSize = if (keepSetLength || validWrongNumbers.isNotEmpty()) {
            highestNumber
        } else {
            allNumbers.size
        }
        // A number can occupy at most every other position without touching itself.
        val safeRepeat = min(repeat, (roundSize + 1) / 2)
        val maximumImportant = roundSize / safeRepeat
        val importantNumbers = validWrongNumbers
            .shuffled(random)
            .take(min(validWrongNumbers.size, maximumImportant))

        val repeatedImportant = importantNumbers.flatMap { number -> List(safeRepeat) { number } }
        val remainingCount = roundSize - repeatedImportant.size
        val otherNumberPool = allNumbers
            .filterNot { it in importantNumbers }
            .ifEmpty { allNumbers }
        val otherNumbers = fillRound(
            requiredNumbers = otherNumberPool.shuffled(random),
            fillNumbers = otherNumberPool,
            size = remainingCount,
            random = random,
        )

        return arrangeWithoutAdjacentDuplicates(
            numbers = repeatedImportant + otherNumbers,
            random = random,
            forbiddenFirstNumber = previousSecondNumber,
            fallbackNumbers = allNumbers,
        )
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
        forbiddenFirstNumber: Int? = null,
        fallbackNumbers: List<Int> = numbers.distinct(),
    ): List<Int> {
        val firstCandidates = numbers
            .distinct()
            .filter { it != forbiddenFirstNumber }
            .shuffled(random)
        firstCandidates.forEach { firstNumber ->
            tryArrange(numbers, firstNumber, random)?.let { return it }
        }

        // This only occurs for a constrained short round such as [2, 2, 3]
        // following a 2. Cycle through all allowed values to satisfy both
        // adjacency guarantees even though the desired repetition count cannot fit.
        val allowed = fallbackNumbers.distinct()
        require(allowed.size >= 2)
        val fallbackFirst = allowed.filter { it != forbiddenFirstNumber }.random(random)
        val cycle = listOf(fallbackFirst) +
            allowed.filter { it != fallbackFirst }.shuffled(random)
        return List(numbers.size) { index -> cycle[index % cycle.size] }
    }

    private fun tryArrange(
        numbers: List<Int>,
        firstNumber: Int,
        random: Random,
    ): List<Int>? {
        val remaining = numbers.groupingBy { it }.eachCount().toMutableMap()
        val arranged = ArrayList<Int>(numbers.size)

        fun append(number: Int) {
            arranged += number
            val newCount = remaining.getValue(number) - 1
            if (newCount == 0) remaining.remove(number) else remaining[number] = newCount
        }

        append(firstNumber)
        while (remaining.isNotEmpty()) {
            val candidates = remaining.filterKeys { it != arranged.last() }
            if (candidates.isEmpty()) return null
            val highestRemainingCount = candidates.maxOf { it.value }
            append(
                candidates
                    .filterValues { it == highestRemainingCount }
                    .keys
                    .toList()
                    .random(random),
            )
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
