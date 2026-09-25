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
    val orderedNumbers: Boolean,
)

data class TimerHistoryEntry(
    val id: Long,
    val finishedAtMillis: Long,
    val durationMinutes: Int,
    val correctCalculations: Int,
    val numberOfCalculations: Int,
)

data class TimerProgress(
    val minutes: Int,
    val remainingMillis: Long,
    val isArmed: Boolean,
)

data class StopwatchProgress(
    val elapsedMillis: Long,
    val isArmed: Boolean,
)

data class AdditionConfiguration(
    val highestInput: Int?,
    val highestResult: Int?,
    val autoEnter: Boolean,
    val minimumResult: Int,
)

data class SubtractionConfiguration(
    val highestInput: Int?,
    val lowestResult: Int?,
    val autoEnter: Boolean,
    val minimumInput: Int?,
    val maximumResult: Int?,
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
                $COLUMN_AUTO_ENTER INTEGER NOT NULL,
                $COLUMN_ORDERED_NUMBERS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createScoreTable(database)
        createTimerSettingsTable(database)
        createStopwatchSettingsTable(database)
        createTimerHistoryTable(database)
        createSettingsTable(database)
        createNumberAvailabilityTable(database)
        createOperatorSettingsTable(database)
        createAdditionConfigurationTable(database)
        createSubtractionConfigurationTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createScoreTable(database)
        if (oldVersion < 3) createTimerSettingsTable(database)
        if (oldVersion < 4) createTimerHistoryTable(database)
        if (oldVersion < 6) addOrderedNumbersColumn(database)
        if (oldVersion < 7) createSettingsTable(database)
        if (oldVersion < 7) createNumberAvailabilityTable(database)
        if (oldVersion < 8) createOperatorSettingsTable(database)
        if (oldVersion < 9) createAdditionConfigurationTable(database)
        if (oldVersion < 10) addMinimumResultColumn(database)
        if (oldVersion < 11) createSubtractionConfigurationTable(database)
        if (oldVersion < 12) migrateSubtractionRestrictions(database)
        if (oldVersion < 13) addTimerProgressColumns(database)
        if (oldVersion < 14) createStopwatchSettingsTable(database)
    }

    override fun onOpen(database: SQLiteDatabase) {
        super.onOpen(database)
        // Repairs databases opened by the brief version-6 build that did not
        // yet include the migration.
        if (!hasColumn(database, TABLE_CONFIGURATION, COLUMN_ORDERED_NUMBERS)) {
            addOrderedNumbersColumn(database)
        }
    }

    fun save(operator: MathOperator, configuration: PracticeConfiguration) {
        val values = ContentValues().apply {
            put(COLUMN_OPERATOR, operator.name)
            put(COLUMN_HIGHEST_NUMBER, configuration.highestNumber)
            put(COLUMN_RANDOM_FIRST_SECOND, configuration.randomFirstSecond)
            put(COLUMN_WITHOUT_ONE_AND_TEN, configuration.withoutOneAndTen)
            put(COLUMN_AUTO_ENTER, configuration.autoEnter)
            put(COLUMN_ORDERED_NUMBERS, configuration.orderedNumbers)
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
            COLUMN_ORDERED_NUMBERS,
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
            orderedNumbers = cursor.getInt(
                cursor.getColumnIndexOrThrow(COLUMN_ORDERED_NUMBERS),
            ) != 0,
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

    fun saveSettingsPin(pin: String) {
        val values = ContentValues().apply {
            put(COLUMN_SETTINGS_ID, SETTINGS_ROW_ID)
            put(COLUMN_SETTINGS_PIN, pin)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadSettingsPin(): String? = readableDatabase.query(
        TABLE_SETTINGS,
        arrayOf(COLUMN_SETTINGS_PIN),
        "$COLUMN_SETTINGS_ID = ?",
        arrayOf(SETTINGS_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SETTINGS_PIN)) else null
    }

    fun loadAvailableNumbers(operator: MathOperator): Set<Int> = readableDatabase.query(
        TABLE_NUMBER_AVAILABILITY,
        arrayOf(COLUMN_NUMBER, COLUMN_ENABLED),
        "$COLUMN_OPERATOR = ?",
        arrayOf(operator.name),
        null,
        null,
        null,
    ).use { cursor ->
        val available = mutableSetOf<Int>()
        val numberColumn = cursor.getColumnIndexOrThrow(COLUMN_NUMBER)
        val enabledColumn = cursor.getColumnIndexOrThrow(COLUMN_ENABLED)
        var hasSavedRows = false
        while (cursor.moveToNext()) {
            hasSavedRows = true
            if (cursor.getInt(enabledColumn) != 0) available += cursor.getInt(numberColumn)
        }
        if (hasSavedRows) available else (1..12).toSet()
    }

    fun saveAvailableNumbers(operator: MathOperator, numbers: Set<Int>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            (1..12).forEach { number ->
                val values = ContentValues().apply {
                    put(COLUMN_OPERATOR, operator.name)
                    put(COLUMN_NUMBER, number)
                    put(COLUMN_ENABLED, number in numbers)
                }
                database.insertWithOnConflict(
                    TABLE_NUMBER_AVAILABILITY,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun loadShowHints(operator: MathOperator): Boolean = readableDatabase.query(
        TABLE_OPERATOR_SETTINGS,
        arrayOf(COLUMN_SHOW_HINTS),
        "$COLUMN_OPERATOR = ?",
        arrayOf(operator.name),
        null,
        null,
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SHOW_HINTS)) != 0 else true
    }

    fun saveShowHints(operator: MathOperator, showHints: Boolean) {
        val values = ContentValues().apply {
            put(COLUMN_OPERATOR, operator.name)
            put(COLUMN_SHOW_HINTS, showHints)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_OPERATOR_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun saveAdditionConfiguration(configuration: AdditionConfiguration) {
        val values = ContentValues().apply {
            put(COLUMN_ADDITION_ID, ADDITION_ROW_ID)
            configuration.highestInput?.let { put(COLUMN_HIGHEST_INPUT, it) } ?: putNull(COLUMN_HIGHEST_INPUT)
            configuration.highestResult?.let { put(COLUMN_HIGHEST_RESULT, it) } ?: putNull(COLUMN_HIGHEST_RESULT)
            put(COLUMN_AUTO_ENTER, configuration.autoEnter)
            put(COLUMN_MINIMUM_RESULT, configuration.minimumResult)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_ADDITION_CONFIGURATION,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadAdditionConfiguration(): AdditionConfiguration? = readableDatabase.query(
        TABLE_ADDITION_CONFIGURATION,
        arrayOf(COLUMN_HIGHEST_INPUT, COLUMN_HIGHEST_RESULT, COLUMN_AUTO_ENTER, COLUMN_MINIMUM_RESULT),
        "$COLUMN_ADDITION_ID = ?",
        arrayOf(ADDITION_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val inputColumn = cursor.getColumnIndexOrThrow(COLUMN_HIGHEST_INPUT)
        val resultColumn = cursor.getColumnIndexOrThrow(COLUMN_HIGHEST_RESULT)
        AdditionConfiguration(
            highestInput = if (cursor.isNull(inputColumn)) null else cursor.getInt(inputColumn),
            highestResult = if (cursor.isNull(resultColumn)) null else cursor.getInt(resultColumn),
            autoEnter = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AUTO_ENTER)) != 0,
            minimumResult = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MINIMUM_RESULT)),
        )
    }

    fun saveSubtractionConfiguration(configuration: SubtractionConfiguration) {
        val values = ContentValues().apply {
            put(COLUMN_SUBTRACTION_ID, SUBTRACTION_ROW_ID)
            configuration.highestInput?.let { put(COLUMN_HIGHEST_INPUT, it) } ?: putNull(COLUMN_HIGHEST_INPUT)
            configuration.lowestResult?.let { put(COLUMN_LOWEST_RESULT, it) } ?: putNull(COLUMN_LOWEST_RESULT)
            put(COLUMN_AUTO_ENTER, configuration.autoEnter)
            configuration.minimumInput?.let { put(COLUMN_SUBTRACTION_MINIMUM_INPUT, it) }
                ?: putNull(COLUMN_SUBTRACTION_MINIMUM_INPUT)
            configuration.maximumResult?.let { put(COLUMN_SUBTRACTION_MAXIMUM_RESULT, it) }
                ?: putNull(COLUMN_SUBTRACTION_MAXIMUM_RESULT)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SUBTRACTION_CONFIGURATION,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadSubtractionConfiguration(): SubtractionConfiguration? = readableDatabase.query(
        TABLE_SUBTRACTION_CONFIGURATION,
        arrayOf(
            COLUMN_HIGHEST_INPUT,
            COLUMN_LOWEST_RESULT,
            COLUMN_AUTO_ENTER,
            COLUMN_SUBTRACTION_MINIMUM_INPUT,
            COLUMN_SUBTRACTION_MAXIMUM_RESULT,
        ),
        "$COLUMN_SUBTRACTION_ID = ?",
        arrayOf(SUBTRACTION_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val inputColumn = cursor.getColumnIndexOrThrow(COLUMN_HIGHEST_INPUT)
        val resultColumn = cursor.getColumnIndexOrThrow(COLUMN_LOWEST_RESULT)
        val minimumColumn = cursor.getColumnIndexOrThrow(COLUMN_SUBTRACTION_MINIMUM_INPUT)
        val maximumColumn = cursor.getColumnIndexOrThrow(COLUMN_SUBTRACTION_MAXIMUM_RESULT)
        SubtractionConfiguration(
            highestInput = if (cursor.isNull(inputColumn)) null else cursor.getInt(inputColumn),
            lowestResult = if (cursor.isNull(resultColumn)) null else cursor.getInt(resultColumn),
            autoEnter = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AUTO_ENTER)) != 0,
            minimumInput = if (cursor.isNull(minimumColumn)) null else cursor.getInt(minimumColumn),
            maximumResult = if (cursor.isNull(maximumColumn)) null else cursor.getInt(maximumColumn),
        )
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

    fun saveTimerProgress(minutes: Int, remainingMillis: Long, isArmed: Boolean) {
        val values = ContentValues().apply {
            put(COLUMN_TIMER_ID, TIMER_ROW_ID)
            put(COLUMN_TIMER_MINUTES, minutes)
            put(COLUMN_TIMER_REMAINING_MILLIS, remainingMillis)
            put(COLUMN_TIMER_IS_ARMED, isArmed)
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

    fun loadTimerProgress(): TimerProgress? = readableDatabase.query(
        TABLE_TIMER_SETTINGS,
        arrayOf(COLUMN_TIMER_MINUTES, COLUMN_TIMER_REMAINING_MILLIS, COLUMN_TIMER_IS_ARMED),
        "$COLUMN_TIMER_ID = ?",
        arrayOf(TIMER_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        TimerProgress(
            minutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIMER_MINUTES)),
            remainingMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMER_REMAINING_MILLIS)),
            isArmed = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIMER_IS_ARMED)) != 0,
        )
    }

    fun saveStopwatchProgress(elapsedMillis: Long, isArmed: Boolean) {
        val values = ContentValues().apply {
            put(COLUMN_STOPWATCH_ID, STOPWATCH_ROW_ID)
            put(COLUMN_STOPWATCH_ELAPSED_MILLIS, elapsedMillis)
            put(COLUMN_STOPWATCH_IS_ARMED, isArmed)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_STOPWATCH_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun loadStopwatchProgress(): StopwatchProgress? = readableDatabase.query(
        TABLE_STOPWATCH_SETTINGS,
        arrayOf(COLUMN_STOPWATCH_ELAPSED_MILLIS, COLUMN_STOPWATCH_IS_ARMED),
        "$COLUMN_STOPWATCH_ID = ?",
        arrayOf(STOPWATCH_ROW_ID.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        StopwatchProgress(
            elapsedMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_STOPWATCH_ELAPSED_MILLIS)),
            isArmed = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STOPWATCH_IS_ARMED)) != 0,
        )
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

    private fun addOrderedNumbersColumn(database: SQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE $TABLE_CONFIGURATION ADD COLUMN " +
                "$COLUMN_ORDERED_NUMBERS INTEGER NOT NULL DEFAULT 0",
        )
    }

    private fun hasColumn(database: SQLiteDatabase, table: String, column: String): Boolean =
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == column) return@use true
            }
            false
        }

    private fun createTimerSettingsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TIMER_SETTINGS (
                $COLUMN_TIMER_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMER_MINUTES INTEGER NOT NULL,
                $COLUMN_TIMER_REMAINING_MILLIS INTEGER NOT NULL DEFAULT 0,
                $COLUMN_TIMER_IS_ARMED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    private fun addTimerProgressColumns(database: SQLiteDatabase) {
        if (!hasColumn(database, TABLE_TIMER_SETTINGS, COLUMN_TIMER_REMAINING_MILLIS)) {
            database.execSQL(
                "ALTER TABLE $TABLE_TIMER_SETTINGS ADD COLUMN " +
                    "$COLUMN_TIMER_REMAINING_MILLIS INTEGER NOT NULL DEFAULT 0",
            )
        }
        if (!hasColumn(database, TABLE_TIMER_SETTINGS, COLUMN_TIMER_IS_ARMED)) {
            database.execSQL(
                "ALTER TABLE $TABLE_TIMER_SETTINGS ADD COLUMN " +
                    "$COLUMN_TIMER_IS_ARMED INTEGER NOT NULL DEFAULT 0",
            )
        }
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

    private fun createStopwatchSettingsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STOPWATCH_SETTINGS (
                $COLUMN_STOPWATCH_ID INTEGER PRIMARY KEY,
                $COLUMN_STOPWATCH_ELAPSED_MILLIS INTEGER NOT NULL,
                $COLUMN_STOPWATCH_IS_ARMED INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createNumberAvailabilityTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NUMBER_AVAILABILITY (
                $COLUMN_OPERATOR TEXT NOT NULL,
                $COLUMN_NUMBER INTEGER NOT NULL,
                $COLUMN_ENABLED INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_OPERATOR, $COLUMN_NUMBER)
            )
            """.trimIndent(),
        )
    }

    private fun createSettingsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                $COLUMN_SETTINGS_ID INTEGER PRIMARY KEY,
                $COLUMN_SETTINGS_PIN TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createOperatorSettingsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OPERATOR_SETTINGS (
                $COLUMN_OPERATOR TEXT PRIMARY KEY,
                $COLUMN_SHOW_HINTS INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
    }

    private fun createAdditionConfigurationTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ADDITION_CONFIGURATION (
                $COLUMN_ADDITION_ID INTEGER PRIMARY KEY,
                $COLUMN_HIGHEST_INPUT INTEGER,
                $COLUMN_HIGHEST_RESULT INTEGER,
                $COLUMN_AUTO_ENTER INTEGER NOT NULL DEFAULT 0,
                $COLUMN_MINIMUM_RESULT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    private fun addMinimumResultColumn(database: SQLiteDatabase) {
        if (!hasColumn(database, TABLE_ADDITION_CONFIGURATION, COLUMN_MINIMUM_RESULT)) {
            database.execSQL(
                "ALTER TABLE $TABLE_ADDITION_CONFIGURATION ADD COLUMN " +
                    "$COLUMN_MINIMUM_RESULT INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    private fun createSubtractionConfigurationTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SUBTRACTION_CONFIGURATION (
                $COLUMN_SUBTRACTION_ID INTEGER PRIMARY KEY,
                $COLUMN_HIGHEST_INPUT INTEGER,
                $COLUMN_LOWEST_RESULT INTEGER,
                $COLUMN_AUTO_ENTER INTEGER NOT NULL DEFAULT 0,
                $COLUMN_SUBTRACTION_MINIMUM_INPUT INTEGER,
                $COLUMN_SUBTRACTION_MAXIMUM_RESULT INTEGER
            )
            """.trimIndent(),
        )
    }

    private fun migrateSubtractionRestrictions(database: SQLiteDatabase) {
        if (hasColumn(database, TABLE_SUBTRACTION_CONFIGURATION, COLUMN_SUBTRACTION_MINIMUM_RESULT) &&
            !hasColumn(database, TABLE_SUBTRACTION_CONFIGURATION, COLUMN_SUBTRACTION_MINIMUM_INPUT)
        ) {
            database.execSQL(
                "ALTER TABLE $TABLE_SUBTRACTION_CONFIGURATION RENAME COLUMN " +
                    "$COLUMN_SUBTRACTION_MINIMUM_RESULT TO $COLUMN_SUBTRACTION_MINIMUM_INPUT",
            )
        }
        if (!hasColumn(database, TABLE_SUBTRACTION_CONFIGURATION, COLUMN_SUBTRACTION_MINIMUM_INPUT)) {
            database.execSQL(
                "ALTER TABLE $TABLE_SUBTRACTION_CONFIGURATION ADD COLUMN " +
                    "$COLUMN_SUBTRACTION_MINIMUM_INPUT INTEGER",
            )
        }
        if (!hasColumn(database, TABLE_SUBTRACTION_CONFIGURATION, COLUMN_SUBTRACTION_MAXIMUM_RESULT)) {
            database.execSQL(
                "ALTER TABLE $TABLE_SUBTRACTION_CONFIGURATION ADD COLUMN " +
                    "$COLUMN_SUBTRACTION_MAXIMUM_RESULT INTEGER",
            )
        }
    }

    private companion object {
        const val DATABASE_NAME = "times_tables.db"
        const val DATABASE_VERSION = 14
        const val TABLE_CONFIGURATION = "practice_configuration"
        const val TABLE_SCORE = "last_score"
        const val TABLE_TIMER_SETTINGS = "timer_settings"
        const val TABLE_TIMER_HISTORY = "timer_history"
        const val TABLE_STOPWATCH_SETTINGS = "stopwatch_settings"
        const val TABLE_SETTINGS = "settings"
        const val TABLE_NUMBER_AVAILABILITY = "number_availability"
        const val TABLE_OPERATOR_SETTINGS = "operator_settings"
        const val TABLE_ADDITION_CONFIGURATION = "addition_configuration"
        const val TABLE_SUBTRACTION_CONFIGURATION = "subtraction_configuration"
        const val COLUMN_OPERATOR = "operator"
        const val COLUMN_HIGHEST_NUMBER = "highest_number"
        const val COLUMN_RANDOM_FIRST_SECOND = "random_first_second"
        const val COLUMN_WITHOUT_ONE_AND_TEN = "without_one_and_ten"
        const val COLUMN_AUTO_ENTER = "auto_enter"
        const val COLUMN_ORDERED_NUMBERS = "ordered_numbers"
        const val COLUMN_FIRST_NUMBER = "first_number"
        const val COLUMN_ERROR_COUNT = "error_count"
        const val COLUMN_TIMER_ID = "id"
        const val COLUMN_TIMER_MINUTES = "minutes"
        const val COLUMN_TIMER_REMAINING_MILLIS = "remaining_millis"
        const val COLUMN_TIMER_IS_ARMED = "is_armed"
        const val TIMER_ROW_ID = 1
        const val COLUMN_STOPWATCH_ID = "id"
        const val COLUMN_STOPWATCH_ELAPSED_MILLIS = "elapsed_millis"
        const val COLUMN_STOPWATCH_IS_ARMED = "is_armed"
        const val STOPWATCH_ROW_ID = 1
        const val COLUMN_HISTORY_ID = "history_id"
        const val COLUMN_FINISHED_AT = "finished_at"
        const val COLUMN_DURATION_MINUTES = "duration_minutes"
        const val COLUMN_CORRECT_CALCULATIONS = "correct_calculations"
        const val COLUMN_NUMBER_OF_CALCULATIONS = "number_of_calculations"
        const val COLUMN_SETTINGS_ID = "id"
        const val COLUMN_SETTINGS_PIN = "pin"
        const val COLUMN_NUMBER = "number"
        const val COLUMN_ENABLED = "enabled"
        const val COLUMN_SHOW_HINTS = "show_hints"
        const val COLUMN_ADDITION_ID = "id"
        const val COLUMN_HIGHEST_INPUT = "highest_input"
        const val COLUMN_HIGHEST_RESULT = "highest_result"
        const val COLUMN_MINIMUM_RESULT = "minimum_result"
        const val COLUMN_SUBTRACTION_ID = "id"
        const val COLUMN_LOWEST_RESULT = "lowest_result"
        const val COLUMN_SUBTRACTION_MINIMUM_RESULT = "subtraction_minimum_result"
        const val COLUMN_SUBTRACTION_MINIMUM_INPUT = "subtraction_minimum_input"
        const val COLUMN_SUBTRACTION_MAXIMUM_RESULT = "subtraction_maximum_result"
        const val SUBTRACTION_ROW_ID = 1
        const val ADDITION_ROW_ID = 1
        const val SETTINGS_ROW_ID = 1
    }
}
