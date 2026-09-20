# 1x1 Android App

## Overview

This is a Kotlin Android app built with Jetpack Compose for children to practice arithmetic. The app uses a fixed light/gray visual theme and supports:

- Addition (`+`)
- Subtraction (`-`)
- Multiplication (`×`)
- Division (`÷`)
- Mixed multiplication/division (`×÷`)
- Wildcard table selection (`?`) for multiplication, division, and mixed modes

## Main structure

- `app/src/main/java/com/danielhoop/timestables/MainActivity.kt` contains the Compose screens, dialogs, setup forms, parental settings UI, hints, timer UI, and navigation.
- `PracticeViewModel.kt` owns screen state, configuration loading/saving, calculation progress, timer activity, scoring, retries, and validation.
- `PracticeEngine.kt` contains pure calculation generation, review-round generation, hint generation, and arithmetic models. Keep this file free of Android APIs so it remains unit-testable.
- `ConfigurationDatabase.kt` contains the SQLite schema, persistence methods, and migrations.
- `PracticeEngineTest.kt` contains JVM unit tests for generation, hints, mixed/wildcard sampling, addition, and subtraction.

## Persistence

SQLite database name: `times_tables.db`.

The database stores practice configurations, parental number availability, hint preferences, scores, timer settings/history, the parental PIN, addition settings, and subtraction settings. The current schema version is defined in `ConfigurationDatabase.kt`; any schema change must include an incremental `onUpgrade` migration.

Parental settings are PIN-gated. Number availability defaults to all numbers enabled. Unset addition/subtraction restrictions mean no restriction.

## Calculation behavior

- Multiplication/division/mixed table practice uses first-half and review-half rounds.
- Wrong answers open the correction flow and require the correct answer before proceeding.
- Wildcard mode samples up to 10 unique calculations from allowed base numbers and safely handles an empty or over-restricted pool.
- Addition and subtraction use 20 unique samples where enough combinations exist, with the same correction/retry behavior.
- Auto-enter is controlled per applicable setup configuration.
- Hints are generated in `PracticeEngine` and can be disabled independently for multiplication and division.

## Development commands

Use the configured JDK at `C:\Program Files\Android\Android Studio 1\jbr` when needed:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio 1\jbr'
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache
.\gradlew.bat :app:compileDebugKotlin --no-configuration-cache
```

Run the unit tests after changes to calculation generation, persistence, validation, or UI state. Do not remove existing user changes in the working tree.
