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
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    private companion object {
        const val DATABASE_NAME = "times_tables.db"
        const val DATABASE_VERSION = 1
        const val TABLE_CONFIGURATION = "practice_configuration"
        const val COLUMN_OPERATOR = "operator"
        const val COLUMN_HIGHEST_NUMBER = "highest_number"
        const val COLUMN_RANDOM_FIRST_SECOND = "random_first_second"
        const val COLUMN_WITHOUT_ONE_AND_TEN = "without_one_and_ten"
        const val COLUMN_AUTO_ENTER = "auto_enter"
    }
}
