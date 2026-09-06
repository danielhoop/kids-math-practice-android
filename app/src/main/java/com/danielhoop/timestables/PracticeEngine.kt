package com.danielhoop.timestables

import kotlin.math.min
import kotlin.random.Random

const val REPEAT_WRONG_NUMBER = 3
const val forceSetLength = false
private const val HINT_SEPARATOR = "----------------"
private val FIVE_BASED_HINT_TARGETS = setOf(4, 6, 7, 8)
private val HintPriority = listOf(2, 6, 4, 9, 7, 3, 8)

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

/** Returns the optional, monospaced arithmetic hint for a multiplication table. */
fun Calculation.hintLines(): List<String>? {
    if (operator != MathOperator.MULTIPLY) return null
    val factors = listOf(firstNumber, secondNumber)
    val eligibleTargets = factors
        .filter {
            it in HintPriority &&
                !(5 in factors && it in FIVE_BASED_HINT_TARGETS)
        }
        .distinct()
        .sortedBy { HintPriority.indexOf(it) }
    val fiveComplement = fiveComplementHintLines(firstNumber, secondNumber)
    if (eligibleTargets.isEmpty() && fiveComplement == null) return null

    val approaches = buildList {
        addAll(eligibleTargets.map { target ->
            val x = factors.firstOrNull { it != target } ?: target
            hintLinesForTarget(target, x)
        })
        fiveComplement?.let(::add)
    }
    return approaches.flatMapIndexed { index, linesForApproach ->
        buildList {
            if (index > 0) {
                add("")
                add(HINT_SEPARATOR)
                add("")
            }
            addAll(linesForApproach)
        }
    }
}

private fun fiveComplementHintLines(first: Int, second: Int): List<String>? {
    if (first != 5 && second != 5) return null
    val other = if (first == 5) second else first
    val complement = when (other) {
        3 -> 2
        5 -> 4
        7 -> 6
        9 -> 8
        else -> return null
    }
    val firstResult = complement * 5
    val answer = firstResult + 5
    val width = maxOf(complement, 5, firstResult, answer).digits()
    fun n(value: Int) = value.toString().padStart(width, ' ')
    return listOf(
        "${n(complement)} × ${n(5)} = ${n(firstResult)}",
        "${n(firstResult)} + ${n(5)} = ${n(answer)}",
    )
}

private fun hintLinesForTarget(target: Int, x: Int): List<String> {
    val fiveTimes = 5 * x
    val tenTimes = 10 * x
    val answer = target * x
    val width = when (target) {
        2 -> maxOf(x, 2 * x).digits()
        3 -> maxOf(x, 3 * x).digits()
        4, 6, 7 -> maxOf(x, fiveTimes, answer).digits()
        8, 9 -> maxOf(x, tenTimes, answer).digits()
        else -> return emptyList()
    }
    fun n(value: Int) = value.toString().padStart(width, ' ')
    fun blank() = "_".repeat(width)

    return when (target) {
        2 -> listOf("${n(x)} + ${n(x)} = ${blank()}")
        3 -> listOf("${n(x)} + ${n(x)} + ${n(x)} = ${blank()}")
        4 -> listOf(
            "5 × ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} - ${n(x)} = ${blank()}",
        )
        6 -> listOf(
            "5 × ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} + ${n(x)} = ${blank()}",
        )
        7 -> listOf(
            "5 × ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} + ${n(x)} + ${n(x)} = ${blank()}",
        )
        8 -> listOf(
            "10 × ${n(x)} = ${n(tenTimes)}",
            "${n(tenTimes)} - ${n(x)} - ${n(x)} = ${blank()}",
        )
        9 -> listOf(
            "10 × ${n(x)} = ${n(tenTimes)}",
            "${n(tenTimes)} - ${n(x)} = ${blank()}",
        )
        else -> emptyList()
    }
}

private fun Int.digits(): Int = toString().length

/** Pure practice-row generation, kept free of Android APIs so it is easy to test. */
object PracticeEngine {
    fun firstRoundNumbers(
        highestNumber: Int,
        withoutOneAndTen: Boolean = false,
        keepSetLength: Boolean = forceSetLength,
        orderedNumbers: Boolean = false,
        random: Random = Random.Default,
    ): List<Int> {
        require(highestNumber > 0)
        val allowedNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allowedNumbers.isNotEmpty())
        require(highestNumber == 1 || allowedNumbers.size >= 2)
        val roundSize = if (keepSetLength) highestNumber else allowedNumbers.size
        if (orderedNumbers) {
            return List(roundSize) { index -> allowedNumbers[index % allowedNumbers.size] }
        }
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
        orderedNumbers: Boolean = false,
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
            .let { numbers -> if (orderedNumbers) numbers.sorted() else numbers.shuffled(random) }
            .take(min(validWrongNumbers.size, maximumImportant))

        val repeatedImportant = importantNumbers.flatMap { number -> List(safeRepeat) { number } }
        val remainingCount = roundSize - repeatedImportant.size
        val otherNumberPool = allNumbers
            .filterNot { it in importantNumbers }
            .ifEmpty { allNumbers }
        val otherNumbers = fillRound(
            requiredNumbers = if (orderedNumbers) otherNumberPool else otherNumberPool.shuffled(random),
            fillNumbers = otherNumberPool,
            size = remainingCount,
            random = if (orderedNumbers) Random(0) else random,
        )

        if (orderedNumbers && importantNumbers.isEmpty()) {
            return List(roundSize) { index -> allNumbers[index % allNumbers.size] }
        }

        return arrangeWithoutAdjacentDuplicates(
            numbers = repeatedImportant + otherNumbers,
            random = if (orderedNumbers) Random(0) else random,
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
