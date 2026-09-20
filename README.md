# 1x1 Android App

An offline Android app for children to practice arithmetic. It is written in
Kotlin with Jetpack Compose and uses a fixed light-gray appearance.

## Calculation modes

The home screen provides:

- Addition (`+`)
- Subtraction (`-`)
- Multiplication (`×`)
- Division (`÷`)
- Mixed multiplication/division (`×÷`)

Multiplication, division, and mixed mode let the child choose a table number
from 1 through 12. The `?` button samples calculations across all allowed
base numbers. Addition and subtraction use dedicated setup screens without a
table-number selection.

Addition supports `Highest input`, `Highest result`, and `Auto enter`. Exactly
one of the two limits must be entered. Subtraction supports `Highest input`,
`Lowest result`, and `Auto enter`; the lowest result must be smaller than the
highest input.

Each arithmetic mode uses up to 20 calculations where enough unique
combinations exist. Multiplication, division, mixed, and wildcard practice use
review rounds that repeat numbers involved in mistakes. Wrong answers open a
correction dialog and the child must enter the correct answer before
continuing. Auto-enter submits once the expected number of digits has been
typed.

## Hints and parental controls

Multiplication and division hints can be enabled independently. The parental
settings screen is PIN-gated and stores its configuration in SQLite. It
controls:

- Addition minimum result
- Subtraction minimum input and maximum result
- Allowed base numbers for multiplication, division, and mixed mode
- Separate hint visibility for multiplication and division

All number controls default to enabled. Unset limits mean no restriction.

## Persistence and timer

SQLite stores configurations, parental settings, the PIN, scores, and timer
history. The database schema and migrations are in
`app/src/main/java/com/danielhoop/timestables/ConfigurationDatabase.kt`.

The optional timer is configured from the home screen and defaults to 30
minutes. It counts active answer-entry time with a developer-configurable
30-second idle timeout. Finished timers are shown newest-first in the history
dialog with duration and correct/total scores.

## Build and test

Open the project in Android Studio, or run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio 1\jbr'
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache
.\gradlew.bat :app:assembleDebug --no-configuration-cache
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.
