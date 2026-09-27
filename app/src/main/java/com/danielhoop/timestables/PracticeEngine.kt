package com.danielhoop.timestables

import kotlin.math.min
import kotlin.random.Random

const val REPEAT_WRONG_NUMBER = 3
const val MIN_REPEAT_WRONG_NUMBER = 0
const val MAX_REPEAT_WRONG_NUMBER = 5
const val forceSetLength = false
private const val HINT_SEPARATOR = "----------------"
private val FIVE_BASED_HINT_TARGETS = setOf(4, 6, 7, 8)
private val HintPriority = listOf(2, 6, 4, 9, 7, 3, 8)

/** Returns every result digit except the final one, or no hint for a one-digit result. */
fun Int.highestDigitsHint(): String = toString().dropLast(1)

enum class MathOperator(val symbol: String) {
    ADDITION("+"),
    SUBTRACTION("-"),
    MULTIPLY("×"),
    DIVIDE("÷"),
    MIXED("×÷"),
}

data class DisplaySigns(
    val multiplication: String = "×",
    val division: String = "÷",
) {
    fun forOperator(operator: MathOperator): String = when (operator) {
        MathOperator.MULTIPLY -> multiplication
        MathOperator.DIVIDE -> division
        MathOperator.MIXED -> multiplication + division
        else -> operator.symbol
    }
}

data class Calculation(
    val operator: MathOperator,
    val firstNumber: Int,
    val secondNumber: Int,
    /** Controls visual order for multiplication. It is always false for division. */
    val swapRoles: Boolean,
    /** The concrete operation used by a calculation in mixed mode. */
    val calculationOperator: MathOperator = operator,
) {
    val expectedAnswer: Int
        get() = when (calculationOperator) {
            MathOperator.ADDITION -> firstNumber + secondNumber
            MathOperator.SUBTRACTION -> firstNumber - secondNumber
            MathOperator.MULTIPLY -> firstNumber * secondNumber
            MathOperator.DIVIDE -> secondNumber
            MathOperator.MIXED -> error("Mixed mode requires a concrete calculation operator")
        }

    val expression: String
        get() = expression()

    fun expression(signs: DisplaySigns = DisplaySigns()): String = when (calculationOperator) {
        MathOperator.ADDITION -> "$firstNumber ${calculationOperator.symbol} $secondNumber"
        MathOperator.SUBTRACTION -> "$firstNumber ${calculationOperator.symbol} $secondNumber"
        MathOperator.MULTIPLY -> {
            val left = if (swapRoles) firstNumber else secondNumber
            val right = if (swapRoles) secondNumber else firstNumber
            "$left ${signs.forOperator(calculationOperator)} $right"
        }

        MathOperator.DIVIDE -> {
            val product = firstNumber * secondNumber
            "$product ${signs.forOperator(calculationOperator)} $firstNumber"
        }

        MathOperator.MIXED -> error("Mixed mode requires a concrete calculation operator")
    }

}

/** Returns the optional, monospaced arithmetic hint for a multiplication table. */
fun Calculation.hintLines(signs: DisplaySigns = DisplaySigns()): List<String>? {
    if (calculationOperator == MathOperator.DIVIDE) return divisionHintLines(signs)
    if (calculationOperator != MathOperator.MULTIPLY) return null
    val factors = listOf(firstNumber, secondNumber)
    val eligibleTargets = factors
        .filter {
            it in HintPriority &&
                !(5 in factors && it in FIVE_BASED_HINT_TARGETS)
        }
        .distinct()
        .sortedBy { HintPriority.indexOf(it) }
    val fiveComplement = fiveComplementHintLines(firstNumber, secondNumber, signs)
    if (eligibleTargets.isEmpty() && fiveComplement == null) return null

    val approaches = buildList {
        addAll(eligibleTargets.map { target ->
            val x = factors.firstOrNull { it != target } ?: target
            hintLinesForTarget(target, x, signs)
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

private fun Calculation.divisionHintLines(signs: DisplaySigns): List<String> {
    val divisor = firstNumber
    val dividend = firstNumber * secondNumber
    val result = secondNumber
    val anchor = when (result) {
        4, 6, 7 -> 5
        8, 9 -> 10
        else -> null
    }
    val width = maxOf(divisor, dividend, result, anchor ?: 0).digits()
    fun n(value: Int) = value.toString().padStart(width, ' ')
    fun blank() = "_".repeat(width)

    val inverse = "${n(divisor)} ${signs.multiplication} ${blank()} = ${n(dividend)}"
    if (anchor == null) return listOf(inverse)

    val anchorDividend = anchor * divisor
    val difference = when (result) {
        4 -> "${n(anchor)} - ${n(1)} = ${blank()}"
        6 -> "${n(anchor)} + ${n(1)} = ${blank()}"
        7 -> "${n(anchor)} + ${n(1)} + ${n(1)} = ${blank()}"
        8 -> "${n(anchor)} - ${n(1)} - ${n(1)} = ${blank()}"
        9 -> "${n(anchor)} - ${n(1)} = ${blank()}"
        else -> error("Unsupported division hint result")
    }
    return listOf(
        inverse,
        "",
        HINT_SEPARATOR,
        "",
        "${n(anchorDividend)} ${signs.division} ${n(divisor)} = ${n(anchor)}",
        difference,
    )
}

private fun fiveComplementHintLines(first: Int, second: Int, signs: DisplaySigns): List<String>? {
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
        "${n(complement)} ${signs.multiplication} ${n(5)} = ${n(firstResult)}",
        "${n(firstResult)} + ${n(5)} = ${n(answer)}",
    )
}

private fun hintLinesForTarget(target: Int, x: Int, signs: DisplaySigns): List<String> {
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
            "5 ${signs.multiplication} ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} - ${n(x)} = ${blank()}",
        )
        6 -> listOf(
            "5 ${signs.multiplication} ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} + ${n(x)} = ${blank()}",
        )
        7 -> listOf(
            "5 ${signs.multiplication} ${n(x)} = ${n(fiveTimes)}",
            "${n(fiveTimes)} + ${n(x)} + ${n(x)} = ${blank()}",
        )
        8 -> listOf(
            "10 ${signs.multiplication} ${n(x)} = ${n(tenTimes)}",
            "${n(tenTimes)} - ${n(x)} - ${n(x)} = ${blank()}",
        )
        9 -> listOf(
            "10 ${signs.multiplication} ${n(x)} = ${n(tenTimes)}",
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
        roundSizeOverride: Int? = null,
    ): List<Int> {
        require(highestNumber > 0)
        require(repeat in MIN_REPEAT_WRONG_NUMBER..MAX_REPEAT_WRONG_NUMBER)

        val allNumbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(allNumbers.isNotEmpty())
        require(highestNumber == 1 || allNumbers.size >= 2)
        val validWrongNumbers = wrongSecondNumbers.filter { it in allNumbers }
        val roundSize = roundSizeOverride ?: if (keepSetLength || validWrongNumbers.isNotEmpty()) {
            highestNumber
        } else {
            allNumbers.size
        }
        // A number can occupy at most every other position without touching itself.
        val safeRepeat = min(repeat, (roundSize + 1) / 2)
        if (safeRepeat == 0) {
            if (orderedNumbers) {
                return List(roundSize) { index -> allNumbers[index % allNumbers.size] }
            }
            return arrangeWithoutAdjacentDuplicates(
                numbers = fillRound(
                    requiredNumbers = allNumbers.shuffled(random),
                    fillNumbers = allNumbers,
                    size = roundSize,
                    random = random,
                ),
                random = random,
                forbiddenFirstNumber = previousSecondNumber,
                fallbackNumbers = allNumbers,
            )
        }
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
        val calculationOperator = if (operator == MathOperator.MIXED) {
            if (random.nextBoolean()) MathOperator.MULTIPLY else MathOperator.DIVIDE
        } else {
            operator
        }
        Calculation(
            operator = operator,
            firstNumber = firstNumber,
            secondNumber = secondNumber,
            swapRoles = calculationOperator == MathOperator.MULTIPLY &&
                randomFirstSecond && random.nextBoolean(),
            calculationOperator = calculationOperator,
        )
    }

    fun mixedFirstRoundCalculations(
        firstNumber: Int,
        highestNumber: Int,
        withoutOneAndTen: Boolean,
        randomFirstSecond: Boolean,
        roundSize: Int = MIXED_ROUND_LENGTH,
        random: Random = Random.Default,
    ): List<Calculation> {
        val numbers = allowedNumbers(highestNumber, withoutOneAndTen)
        require(numbers.isNotEmpty())
        val pairs = listOf(MathOperator.MULTIPLY, MathOperator.DIVIDE)
            .flatMap { operation -> numbers.map { operation to it } }
            .shuffled(random)
        return pairs.take(min(roundSize, pairs.size)).map { (operation, secondNumber) ->
            Calculation(
                operator = MathOperator.MIXED,
                firstNumber = firstNumber,
                secondNumber = secondNumber,
                swapRoles = operation == MathOperator.MULTIPLY &&
                    randomFirstSecond && random.nextBoolean(),
                calculationOperator = operation,
            )
        }
    }

    fun wildcardFirstRoundCalculations(
        operator: MathOperator,
        baseNumbers: List<Int>,
        secondNumbers: List<Int>,
        randomFirstSecond: Boolean,
        roundSize: Int = MIXED_ROUND_LENGTH,
        random: Random = Random.Default,
        excludedCalculations: Set<Triple<MathOperator, Int, Int>> = emptySet(),
    ): List<Calculation> {
        if (baseNumbers.isEmpty() || secondNumbers.isEmpty()) return emptyList()
        val candidates = baseNumbers.flatMap { baseNumber ->
            secondNumbers.flatMap { secondNumber ->
                val operations = if (operator == MathOperator.MIXED) {
                    listOf(MathOperator.MULTIPLY, MathOperator.DIVIDE)
                } else {
                    listOf(operator)
                }
                operations.map { operation -> Triple(operation, baseNumber, secondNumber) }
            }
        }.shuffled(random)
        return candidates
            .distinctBy { calculationKey(it.first, it.second, it.third) }
            .filterNot { calculationKey(it.first, it.second, it.third) in excludedCalculations }
            .take(roundSize)
            .map { (operation, baseNumber, secondNumber) ->
                Calculation(
                    operator = operator,
                    firstNumber = baseNumber,
                    secondNumber = secondNumber,
                    swapRoles = operation == MathOperator.MULTIPLY &&
                        randomFirstSecond && random.nextBoolean(),
                    calculationOperator = operation,
                )
            }
    }

    fun additionCalculations(
        highestInput: Int?,
        highestResult: Int?,
        minimumResult: Int = 0,
        count: Int = ADDITION_ROUND_LENGTH,
        random: Random = Random.Default,
    ): List<Calculation> {
        val pairs = when {
            highestInput != null -> sampleInputLimitedAdditionPairs(
                highestInput = highestInput,
                minimumResult = minimumResult,
                count = count,
                random = random,
            )
            highestResult != null -> sampleResultLimitedAdditionPairs(
                highestResult = highestResult,
                minimumResult = minimumResult,
                count = count,
                random = random,
            )
            else -> emptyList()
        }
        return pairs
            .map { (first, second) ->
                Calculation(
                    operator = MathOperator.ADDITION,
                    firstNumber = first,
                    secondNumber = second,
                    swapRoles = false,
                )
            }
    }

    fun subtractionCalculations(
        highestInput: Int?,
        lowestResult: Int?,
        count: Int = ADDITION_ROUND_LENGTH,
        random: Random = Random.Default,
    ): List<Calculation> {
        if (highestInput == null || lowestResult == null) return emptyList()
        val availableFirstNumbers = highestInput - lowestResult
        if (availableFirstNumbers <= 0) return emptyList()
        val totalCandidates = availableFirstNumbers.toLong() *
            (availableFirstNumbers + 1L) / 2L
        return sampleDistinctPairs(
            totalCandidates = totalCandidates,
            count = count,
            random = random,
        ) {
            val first = random.nextInt(lowestResult + 1, highestInput + 1)
            first to random.nextInt(1, first - lowestResult + 1)
        }.map { (first, second) ->
            Calculation(
                operator = MathOperator.SUBTRACTION,
                firstNumber = first,
                secondNumber = second,
                swapRoles = false,
            )
        }
    }

    /**
     * Samples only the questions needed for a round.  The former implementation
     * created every possible pair (one million for a limit of 1,000) and shuffled
     * it before selecting 20 questions.
     */
    private fun sampleInputLimitedAdditionPairs(
        highestInput: Int,
        minimumResult: Int,
        count: Int,
        random: Random,
    ): List<Pair<Int, Int>> {
        if (highestInput < 1) return emptyList()
        val minimumSum = maxOf(2, minimumResult)
        if (minimumSum > highestInput * 2) return emptyList()
        val firstStart = maxOf(1, minimumSum - highestInput)
        var totalCandidates = 0L
        for (first in firstStart..highestInput) {
            totalCandidates += highestInput - maxOf(1, minimumSum - first) + 1L
        }
        return sampleDistinctPairs(totalCandidates, count, random) {
            val first = random.nextInt(firstStart, highestInput + 1)
            first to random.nextInt(maxOf(1, minimumSum - first), highestInput + 1)
        }
    }

    private fun sampleResultLimitedAdditionPairs(
        highestResult: Int,
        minimumResult: Int,
        count: Int,
        random: Random,
    ): List<Pair<Int, Int>> {
        val minimumSum = maxOf(2, minimumResult)
        if (highestResult < minimumSum) return emptyList()
        val totalCandidates = positivePairCountAtMost(highestResult) -
            positivePairCountAtMost(minimumSum - 1)
        return sampleDistinctPairs(totalCandidates, count, random) {
            val first = random.nextInt(1, highestResult)
            val maximumSecond = highestResult - first
            first to random.nextInt(maxOf(1, minimumSum - first), maximumSecond + 1)
        }
    }

    private fun positivePairCountAtMost(maximumSum: Int): Long {
        if (maximumSum < 2) return 0L
        val numberOfFirstNumbers = maximumSum.toLong() - 1L
        return numberOfFirstNumbers * (numberOfFirstNumbers + 1L) / 2L
    }

    private fun sampleDistinctPairs(
        totalCandidates: Long,
        count: Int,
        random: Random,
        sample: () -> Pair<Int, Int>,
    ): List<Pair<Int, Int>> {
        val targetSize = minOf(count.toLong(), totalCandidates).toInt()
        if (targetSize <= 0) return emptyList()
        val pairs = LinkedHashSet<Pair<Int, Int>>(targetSize)
        while (pairs.size < targetSize) {
            pairs += sample()
        }
        return pairs.shuffled(random)
    }

    private const val MIXED_ROUND_LENGTH = 10
    private const val ADDITION_ROUND_LENGTH = 20

    private fun calculationKey(
        operation: MathOperator,
        baseNumber: Int,
        secondNumber: Int,
    ): Triple<MathOperator, Int, Int> = if (operation == MathOperator.MULTIPLY) {
        Triple(operation, minOf(baseNumber, secondNumber), maxOf(baseNumber, secondNumber))
    } else {
        Triple(operation, baseNumber, secondNumber)
    }
}
