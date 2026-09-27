package com.danielhoop.timestables

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeEngineTest {
    @Test
    fun firstRoundContainsEveryNumberExactlyOnce() {
        val numbers = PracticeEngine.firstRoundNumbers(10, random = Random(1))

        assertEquals(10, numbers.size)
        assertEquals((1..10).toSet(), numbers.toSet())
    }

    @Test
    fun reviewRoundRepeatsWrongNumbersThreeTimes() {
        val numbers = PracticeEngine.reviewRoundNumbers(
            highestNumber = 10,
            wrongSecondNumbers = setOf(4, 7),
            random = Random(2),
        )

        assertEquals(10, numbers.size)
        assertEquals(3, numbers.count { it == 4 })
        assertEquals(3, numbers.count { it == 7 })
        assertTrue(
            numbers.groupingBy { it }.eachCount()
                .filterKeys { it !in setOf(4, 7) }
                .values.all { it == 1 },
        )
    }

    @Test
    fun reviewRoundLimitsImportantNumbersToAvailableSlots() {
        val numbers = PracticeEngine.reviewRoundNumbers(
            highestNumber = 10,
            wrongSecondNumbers = setOf(4, 5, 6, 7),
            random = Random(3),
        )

        assertEquals(10, numbers.size)
        assertEquals(3, numbers.groupingBy { it }.eachCount().count { it.value == 3 })
        assertTrue(numbers.all { it in 1..10 })
    }

    @Test
    fun noMistakesProducesAnotherCompleteShuffledRound() {
        val numbers = PracticeEngine.reviewRoundNumbers(6, emptySet(), random = Random(4))

        assertEquals((1..6).toSet(), numbers.toSet())
        assertEquals(6, numbers.size)
    }

    @Test
    fun forcedSetLengthExcludesOneAndTenAndRecyclesOtherNumbers() {
        val numbers = PracticeEngine.firstRoundNumbers(
            highestNumber = 12,
            withoutOneAndTen = true,
            keepSetLength = true,
            random = Random(6),
        )

        assertEquals(12, numbers.size)
        assertTrue(1 !in numbers)
        assertTrue(10 !in numbers)
        assertTrue((2..9).all { it in numbers })
        assertTrue(11 in numbers)
        assertTrue(12 in numbers)
        assertNoAdjacentDuplicates(numbers)
    }

    @Test
    fun flexibleSetLengthDoesNotRecycleExcludedNumbers() {
        val numbers = PracticeEngine.firstRoundNumbers(
            highestNumber = 12,
            withoutOneAndTen = true,
            keepSetLength = false,
            random = Random(8),
        )

        assertEquals(10, numbers.size)
        assertEquals((2..9).toSet() + setOf(11, 12), numbers.toSet())
        assertTrue(numbers.groupingBy { it }.eachCount().values.all { it == 1 })
    }

    @Test
    fun flexibleErrorFreeReviewHasSameShortLengthAsFirstRound() {
        val numbers = PracticeEngine.reviewRoundNumbers(
            highestNumber = 10,
            wrongSecondNumbers = emptySet(),
            withoutOneAndTen = true,
            keepSetLength = false,
            random = Random(9),
        )

        assertEquals(8, numbers.size)
        assertEquals((2..9).toSet(), numbers.toSet())
    }

    @Test
    fun orderedFirstRoundUsesIncreasingAllowedNumbers() {
        val numbers = PracticeEngine.firstRoundNumbers(
            highestNumber = 12,
            withoutOneAndTen = true,
            orderedNumbers = true,
            random = Random(10),
        )

        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 11, 12), numbers)
    }

    @Test
    fun orderedErrorFreeReviewUsesIncreasingAllowedNumbers() {
        val numbers = PracticeEngine.reviewRoundNumbers(
            highestNumber = 10,
            wrongSecondNumbers = emptySet(),
            withoutOneAndTen = true,
            orderedNumbers = true,
            random = Random(11),
        )

        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9), numbers)
    }

    @Test
    fun reviewRoundAlsoExcludesOneAndTen() {
        val numbers = PracticeEngine.reviewRoundNumbers(
            highestNumber = 10,
            wrongSecondNumbers = setOf(1, 2, 10),
            withoutOneAndTen = true,
            random = Random(7),
        )

        assertEquals(10, numbers.size)
        assertTrue(1 !in numbers)
        assertTrue(10 !in numbers)
        assertEquals(3, numbers.count { it == 2 })
        assertNoAdjacentDuplicates(numbers)
    }

    @Test
    fun divisionAlwaysKeepsChosenNumberAsDivisor() {
        val normal = Calculation(MathOperator.DIVIDE, 3, 7, swapRoles = false)
        val generated = PracticeEngine.calculations(
            operator = MathOperator.DIVIDE,
            firstNumber = 3,
            secondNumbers = listOf(7),
            randomFirstSecond = true,
            random = Random(5),
        ).single()

        assertEquals("21 ÷ 3", normal.expression)
        assertEquals(7, normal.expectedAnswer)
        assertEquals("21 ÷ 3", generated.expression)
        assertEquals(7, generated.expectedAnswer)
        assertEquals(false, generated.swapRoles)
    }

    @Test
    fun customizedDisplaySignsAreUsedInQuestionsAndHints() {
        val signs = DisplaySigns(multiplication = "·", division = "/")

        assertEquals(
            "6 · 4",
            Calculation(MathOperator.MULTIPLY, 4, 6, swapRoles = false).expression(signs),
        )
        assertEquals(
            "21 / 3",
            Calculation(MathOperator.DIVIDE, 3, 7, swapRoles = false).expression(signs),
        )
        assertEquals(
            "3 · _ = 6",
            Calculation(MathOperator.DIVIDE, 3, 2, swapRoles = false).hintLines(signs)?.first(),
        )
    }

    @Test
    fun hintsAreAvailableForMultiplicationAndDivision() {
        assertTrue(Calculation(MathOperator.MULTIPLY, 2, 6, false).hintLines() != null)
        assertTrue(Calculation(MathOperator.MULTIPLY, 9, 6, false).hintLines() != null)
        assertEquals(null, Calculation(MathOperator.MULTIPLY, 1, 10, false).hintLines())
        assertTrue(Calculation(MathOperator.DIVIDE, 3, 6, false).hintLines() != null)
    }

    @Test
    fun highestDigitsHintLeavesOnlyTheFinalDigitToEnter() {
        assertEquals("", 5.highestDigitsHint())
        assertEquals("1", 15.highestDigitsHint())
        assertEquals("10", 108.highestDigitsHint())
    }

    @Test
    fun fourTableHintUsesFiveTimesThenSubtracts() {
        val lines = Calculation(MathOperator.MULTIPLY, 4, 10, false).hintLines()

        assertEquals(listOf("5 × 10 = 50", "50 - 10 = __"), lines?.take(2))
    }

    @Test
    fun eitherMultiplicationFactorCanQualifyForAHint() {
        val lines = Calculation(MathOperator.MULTIPLY, 5, 4, false).hintLines()

        assertEquals(null, lines)
    }

    @Test
    fun fiveSuppressesHintsThatAlreadyStartWithFiveTimes() {
        assertEquals(null, Calculation(MathOperator.MULTIPLY, 5, 6, false).hintLines())
        assertEquals(null, Calculation(MathOperator.MULTIPLY, 8, 5, false).hintLines())
        assertTrue(Calculation(MathOperator.MULTIPLY, 5, 2, false).hintLines() != null)
        assertTrue(Calculation(MathOperator.MULTIPLY, 5, 9, false).hintLines() != null)
    }

    @Test
    fun fiveComplementHintsAreAppendedAsLastPriority() {
        assertEquals(
            listOf(
                " 5 +  5 +  5 = __",
                "",
                "----------------",
                "",
                " 2 ×  5 = 10",
                "10 +  5 = 15",
            ),
            Calculation(MathOperator.MULTIPLY, 5, 3, false).hintLines(),
        )
        assertEquals(
            listOf(" 4 ×  5 = 20", "20 +  5 = 25"),
            Calculation(MathOperator.MULTIPLY, 5, 5, false).hintLines(),
        )
        assertEquals(
            listOf(" 6 ×  5 = 30", "30 +  5 = 35"),
            Calculation(MathOperator.MULTIPLY, 7, 5, false).hintLines(),
        )
        assertEquals(
            listOf(
                "10 ×  5 = 50",
                "50 -  5 = __",
                "",
                "----------------",
                "",
                " 8 ×  5 = 40",
                "40 +  5 = 45",
            ),
            Calculation(MathOperator.MULTIPLY, 9, 5, false).hintLines(),
        )
    }

    @Test
    fun multipleHintsUseTheRequestedPriorityAndSeparator() {
        val lines = Calculation(MathOperator.MULTIPLY, 2, 6, false).hintLines()

        assertEquals(" 6 +  6 = __", lines?.first())
        assertEquals("", lines?.get(1))
        assertEquals("----------------", lines?.get(2))
        assertEquals("", lines?.get(3))
        assertEquals("5 ×  2 = 10", lines?.get(4))
    }

    @Test
    fun divisionHintsAlwaysStartWithInverseMultiplication() {
        assertEquals(
            listOf("3 × _ = 6"),
            Calculation(MathOperator.DIVIDE, 3, 2, false).hintLines(),
        )
        assertEquals(
            listOf("3 × _ = 9"),
            Calculation(MathOperator.DIVIDE, 3, 3, false).hintLines(),
        )
    }

    @Test
    fun divisionAdjustmentHintsApplyOnlyToResultsFourThroughNineExceptFive() {
        val expected = mapOf(
            4 to listOf(" 3 × __ = 12", "", "----------------", "", "15 ÷  3 =  5", " 5 -  1 = __"),
            6 to listOf(" 3 × __ = 18", "", "----------------", "", "15 ÷  3 =  5", " 5 +  1 = __"),
            7 to listOf(" 3 × __ = 21", "", "----------------", "", "15 ÷  3 =  5", " 5 +  1 +  1 = __"),
            8 to listOf(" 3 × __ = 24", "", "----------------", "", "30 ÷  3 = 10", "10 -  1 -  1 = __"),
            9 to listOf(" 3 × __ = 27", "", "----------------", "", "30 ÷  3 = 10", "10 -  1 = __"),
        )
        expected.forEach { (result, lines) ->
            assertEquals(lines, Calculation(MathOperator.DIVIDE, 3, result, false).hintLines())
        }
    }

    @Test
    fun mixedFirstRoundUsesUniqueOperatorAndNumberPairs() {
        val calculations = PracticeEngine.mixedFirstRoundCalculations(
            firstNumber = 3,
            highestNumber = 10,
            withoutOneAndTen = true,
            randomFirstSecond = true,
            random = Random(11),
        )

        assertEquals(10, calculations.size)
        assertEquals(
            10,
            calculations.map { it.calculationOperator to it.secondNumber }.toSet().size,
        )
        assertTrue(calculations.all { it.operator == MathOperator.MIXED })
    }

    @Test
    fun wildcardFirstRoundSamplesUniqueCalculationsAndHandlesEmptyPools() {
        val calculations = PracticeEngine.wildcardFirstRoundCalculations(
            operator = MathOperator.MIXED,
            baseNumbers = (2..9).toList(),
            secondNumbers = (2..9).toList(),
            randomFirstSecond = true,
            random = Random(12),
        )

        assertEquals(10, calculations.size)
        assertEquals(
            10,
            calculations.map { Triple(it.calculationOperator, it.firstNumber, it.secondNumber) }.toSet().size,
        )
        assertTrue(
            PracticeEngine.wildcardFirstRoundCalculations(
                operator = MathOperator.MULTIPLY,
                baseNumbers = emptyList(),
                secondNumbers = (1..10).toList(),
                randomFirstSecond = false,
            ).isEmpty(),
        )
    }

    @Test
    fun additionSamplesUseEitherInputLimitOrResultLimit() {
        val inputLimited = PracticeEngine.additionCalculations(
            highestInput = 6,
            highestResult = null,
            random = Random(13),
        )
        assertEquals(20, inputLimited.size)
        assertTrue(inputLimited.all { it.calculationOperator == MathOperator.ADDITION })
        assertTrue(inputLimited.map { it.expression }.toSet().size == inputLimited.size)

        val resultLimited = PracticeEngine.additionCalculations(
            highestInput = null,
            highestResult = 10,
            random = Random(14),
        )
        assertEquals(20, resultLimited.size)
        assertTrue(resultLimited.all { it.expectedAnswer <= 10 })

        val minimumResult = PracticeEngine.additionCalculations(
            highestInput = 6,
            highestResult = null,
            minimumResult = 8,
            random = Random(15),
        )
        assertTrue(minimumResult.all { it.expectedAnswer >= 8 })
    }

    @Test
    fun subtractionSamplesRespectLowestResult() {
        val calculations = PracticeEngine.subtractionCalculations(
            highestInput = 10,
            lowestResult = 3,
            random = Random(16),
        )

        assertEquals(20, calculations.size)
        assertTrue(calculations.all { it.expectedAnswer >= 3 })
        assertTrue(calculations.all { it.firstNumber <= 10 && it.secondNumber <= 10 })
    }

    @Test
    fun largeAdditionResultLimitSamplesOnlyTheRequestedDistinctPairs() {
        val calculations = PracticeEngine.additionCalculations(
            highestInput = null,
            highestResult = 100_000,
            random = Random(17),
        )

        assertEquals(20, calculations.size)
        assertEquals(20, calculations.map { it.firstNumber to it.secondNumber }.toSet().size)
        assertTrue(calculations.all { it.expectedAnswer <= 100_000 })
    }

    @Test
    fun largeSubtractionInputLimitSamplesOnlyTheRequestedDistinctPairs() {
        val calculations = PracticeEngine.subtractionCalculations(
            highestInput = 1_000,
            lowestResult = 0,
            random = Random(18),
        )

        assertEquals(20, calculations.size)
        assertEquals(20, calculations.map { it.firstNumber to it.secondNumber }.toSet().size)
        assertTrue(calculations.all { it.expectedAnswer >= 0 })
    }

    @Test
    fun recycledNumbersNeverAppearNextToThemselves() {
        repeat(50) { seed ->
            val firstRound = PracticeEngine.firstRoundNumbers(
                highestNumber = 10,
                withoutOneAndTen = true,
                keepSetLength = true,
                random = Random(seed),
            )
            val reviewRound = PracticeEngine.reviewRoundNumbers(
                highestNumber = 10,
                wrongSecondNumbers = setOf(2, 3, 4),
                withoutOneAndTen = true,
                random = Random(seed),
            )

            assertNoAdjacentDuplicates(firstRound)
            assertNoAdjacentDuplicates(reviewRound)
        }
    }

    @Test
    fun secondRoundDoesNotRepeatTheLastNumberFromFirstRound() {
        repeat(50) { seed ->
            val firstRound = PracticeEngine.firstRoundNumbers(
                highestNumber = 10,
                withoutOneAndTen = true,
                random = Random(seed),
            )
            val reviewRound = PracticeEngine.reviewRoundNumbers(
                highestNumber = 10,
                wrongSecondNumbers = setOf(2, 3, 4),
                withoutOneAndTen = true,
                previousSecondNumber = firstRound.last(),
                random = Random(seed + 100),
            )

            assertTrue(firstRound.last() != reviewRound.first())
            assertNoAdjacentDuplicates(reviewRound)
        }
    }

    @Test
    fun boundaryRuleIsKeptWhenAShortReviewPlanMustBeRebalanced() {
        val reviewRound = PracticeEngine.reviewRoundNumbers(
            highestNumber = 3,
            wrongSecondNumbers = setOf(2),
            withoutOneAndTen = true,
            previousSecondNumber = 2,
            random = Random(101),
        )

        assertTrue(reviewRound.first() != 2)
        assertNoAdjacentDuplicates(reviewRound)
    }

    @Test
    fun reviewRoundAllowsZeroWrongAnswerRepeats() {
        val reviewRound = PracticeEngine.reviewRoundNumbers(
            highestNumber = 6,
            wrongSecondNumbers = setOf(2, 3),
            repeat = 0,
            random = Random(102),
        )

        assertEquals(6, reviewRound.size)
        assertNoAdjacentDuplicates(reviewRound)
    }

    private fun assertNoAdjacentDuplicates(numbers: List<Int>) {
        assertTrue(numbers.zipWithNext().all { (first, second) -> first != second })
    }
}
