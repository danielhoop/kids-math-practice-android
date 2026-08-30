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
    fun firstRoundExcludesOneAndTenAndRecyclesOtherNumbers() {
        val numbers = PracticeEngine.firstRoundNumbers(
            highestNumber = 12,
            withoutOneAndTen = true,
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

        assertEquals("21 : 3", normal.expression)
        assertEquals(7, normal.expectedAnswer)
        assertEquals("21 : 3", generated.expression)
        assertEquals(7, generated.expectedAnswer)
        assertEquals(false, generated.swapRoles)
    }

    @Test
    fun recycledNumbersNeverAppearNextToThemselves() {
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
                random = Random(seed),
            )

            assertNoAdjacentDuplicates(firstRound)
            assertNoAdjacentDuplicates(reviewRound)
        }
    }

    private fun assertNoAdjacentDuplicates(numbers: List<Int>) {
        assertTrue(numbers.zipWithNext().all { (first, second) -> first != second })
    }
}
