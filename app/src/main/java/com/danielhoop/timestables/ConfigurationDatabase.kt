package com.danielhoop.timestables

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PracticeConfiguration(
    val highestNumber: Int,
    val randomFirstSecond: Boolean,
    val withoutOneAndTen: Boolean,
    val autoEnter: Boolean,
)

data class TimerHistoryEntry(
    val id: Long,
    val finishedAtMillis: Long,
    val durationMinutes: Int,
    val correctCalculations: Int,
    val numberOfCalculations: Int,
)

class ConfigurationDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_CONFIGURATION (
                $COLUMN_OPERATOR TEXT PRIMARY KEY,
                $COLUMN_HIGHEST_NUMBER INTEGER NOT NULL,
                $COLUMN_RANDOM_FIRST_SECOND INTEGER NOT NULL,
                $COLUMN_WITHOUT_ONE_AND_TEN INTEGER NOT NULL,
                $COLUMN_AUTO_ENTER INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createScoreTable(database)
        createTimerSettingsTable(database)
        createTimerHistoryTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createScoreTable(database)
        if (oldVersion < 3) createTimerSettingsTable(database)
        if (oldVersion < 4) createTimerHistoryTable(database)
    }

    fun save(operator: MathOperator, configuration: PracticeConfiguration) {
        val values = ContentValues().apply {
            put(COLUMN_OPERATOR, operator.name)
            put(COLUMN_HIGHEST_NUMBER, configuration.highestNumber)
            put(COLUMN_RANDOM_FIRST_SECOND, configuration.randomFirstSecond)
            put(COLUMN_WITHOUT_ONE_AND_TEN, configuration.withoutOneAndTen)
            put(COLUMN_AUTO_ENTER, configuration.autoEnter)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_CONFIGURATION,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun load(operator: MathOperator): PracticeConfiguration? = readableDatabase.query(
        TABLE_CONFIGURATION,
        arrayOf(
            COLUMN_HIGHEST_NUMBER,
            COLUMN_RANDOM_FIRST_SECOND,
            COLUMN_WITHOUT_ONE_AND_TEN,
            COLUMN_AUTO_ENTER,
        ),
        "$COLUMN_OPERATOR = ?",
        arrayOf(operator.name),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        PracticeConfiguration(
            highestNumber = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HIGHEST_NUMBER)),
            randomFirstSecond = cursor.getInt(
                cursor.getColumnIndexOrThrow(COLUMN_RANDOM_FIRST_SECOND),
            ) != 0,
            withoutOneAndTen = cursor.getInt(
                cursor.getColumnIndexOrThrow(COLUMN_WITHOUT_ONE_AND_TEN),
            ) != 0,
            autoEnter = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AUTO_ENTER)) != 0,
        )
    }

    fun saveScore(operator: MathOperator, firstNumber: Int, errorCount: Int) {
        val values = ContentValues().apply {
            put(COLUMN_OPERATOR, operator.name)
            put(COLUMN_FIRST_NUMBER, firstNumber)
            put(COLUMN_ERROR_COUNT, errorCount)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SCORE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadScores(operator: MathOperator): Map<Int, Int> = readableDatabase.query(
        TABLE_SCORE,
        arrayOf(COLUMN_FIRST_NUMBER, COLUMN_ERROR_COUNT),
        "$COLUMN_OPERATOR = ?",
        arrayOf(operator.name),
        null,
        null,
        null,
    ).use { cursor ->
        buildMap {
            val firstNumberColumn = cursor.getColumnIndexOrThrow(COLUMN_FIRST_NUMBER)
            val errorCountColumn = cursor.getColumnIndexOrThrow(COLUMN_ERROR_COUNT)
            while (cursor.moveToNext()) {
                put(cursor.getInt(firstNumberColumn), cursor.getInt(errorCountColumn))
            }
        }
    }

    fun saveTimerMinutes(minutes: Int) {
        val values = ContentValues().apply {
            put(COLUMN_TIMER_ID, TIMER_ROW_ID)
            put(COLUMN_TIMER_MINUTES, minutes)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_TIMER_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadTimerMinutes(): Int? = readableDatabase.query(
        TABLE_TIMER_SETTINGS,
        arrayOf(COLUMN_TIMER_MINUTES),
        "$COLUMN_TIMER_ID = ?",
        arrayOf(TIMER_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIMER_MINUTES))
        } else {
            null
        }
    }

    fun saveTimerCompletion(
        finishedAtMillis: Long,
        durationMinutes: Int,
        correctCalculations: Int,
        numberOfCalculations: Int,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_FINISHED_AT, finishedAtMillis)
            put(COLUMN_DURATION_MINUTES, durationMinutes)
            put(COLUMN_CORRECT_CALCULATIONS, correctCalculations)
            put(COLUMN_NUMBER_OF_CALCULATIONS, numberOfCalculations)
        }
        writableDatabase.insertOrThrow(TABLE_TIMER_HISTORY, null, values)
    }

    fun loadTimerHistory(): List<TimerHistoryEntry> = readableDatabase.query(
        TABLE_TIMER_HISTORY,
        arrayOf(
            COLUMN_HISTORY_ID,
            COLUMN_FINISHED_AT,
            COLUMN_DURATION_MINUTES,
            COLUMN_CORRECT_CALCULATIONS,
            COLUMN_NUMBER_OF_CALCULATIONS,
        ),
        null,
        null,
        null,
        null,
        "$COLUMN_FINISHED_AT DESC, $COLUMN_HISTORY_ID DESC",
    ).use { cursor ->
        buildList {
            val idColumn = cursor.getColumnIndexOrThrow(COLUMN_HISTORY_ID)
            val finishedAtColumn = cursor.getColumnIndexOrThrow(COLUMN_FINISHED_AT)
            val durationColumn = cursor.getColumnIndexOrThrow(COLUMN_DURATION_MINUTES)
            val correctColumn = cursor.getColumnIndexOrThrow(COLUMN_CORRECT_CALCULATIONS)
            val numberColumn = cursor.getColumnIndexOrThrow(COLUMN_NUMBER_OF_CALCULATIONS)
            while (cursor.moveToNext()) {
                add(
                    TimerHistoryEntry(
                        id = cursor.getLong(idColumn),
                        finishedAtMillis = cursor.getLong(finishedAtColumn),
                        durationMinutes = cursor.getInt(durationColumn),
                        correctCalculations = cursor.getInt(correctColumn),
                        numberOfCalculations = cursor.getInt(numberColumn),
                    ),
                )
            }
        }
    }

    private fun createScoreTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCORE (
                $COLUMN_OPERATOR TEXT NOT NULL,
                $COLUMN_FIRST_NUMBER INTEGER NOT NULL,
                $COLUMN_ERROR_COUNT INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_OPERATOR, $COLUMN_FIRST_NUMBER)
            )
            """.trimIndent(),
        )
    }

    private fun createTimerSettingsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TIMER_SETTINGS (
                $COLUMN_TIMER_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMER_MINUTES INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createTimerHistoryTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TIMER_HISTORY (
                $COLUMN_HISTORY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FINISHED_AT INTEGER NOT NULL,
                $COLUMN_DURATION_MINUTES INTEGER NOT NULL,
                $COLUMN_CORRECT_CALCULATIONS INTEGER NOT NULL,
                $COLUMN_NUMBER_OF_CALCULATIONS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val DATABASE_NAME = "times_tables.db"
        const val DATABASE_VERSION = 5
        const val TABLE_CONFIGURATION = "practice_configuration"
        const val TABLE_SCORE = "last_score"
        const val TABLE_TIMER_SETTINGS = "timer_settings"
        const val TABLE_TIMER_HISTORY = "timer_history"
        const val COLUMN_OPERATOR = "operator"
        const val COLUMN_HIGHEST_NUMBER = "highest_number"
        const val COLUMN_RANDOM_FIRST_SECOND = "random_first_second"
        const val COLUMN_WITHOUT_ONE_AND_TEN = "without_one_and_ten"
        const val COLUMN_AUTO_ENTER = "auto_enter"
        const val COLUMN_FIRST_NUMBER = "first_number"
        const val COLUMN_ERROR_COUNT = "error_count"
        const val COLUMN_TIMER_ID = "id"
        const val COLUMN_TIMER_MINUTES = "minutes"
        const val TIMER_ROW_ID = 1
        const val COLUMN_HISTORY_ID = "history_id"
        const val COLUMN_FINISHED_AT = "finished_at"
        const val COLUMN_DURATION_MINUTES = "duration_minutes"
        const val COLUMN_CORRECT_CALCULATIONS = "correct_calculations"
        const val COLUMN_NUMBER_OF_CALCULATIONS = "number_of_calculations"
    }
}
