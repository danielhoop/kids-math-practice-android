# Times Tables

A small, offline Android app for children to practice multiplication and exact
division. It is written in Kotlin with Jetpack Compose.

## Practice flow

1. Choose multiplication (`·`) or division (`:`).
2. Choose a table from 1 through 12 and the highest practice number. For
   multiplication, you can also choose whether the number positions may swap.
3. Complete two rounds. The first includes every number from 1 through the
   configured maximum once. The second repeats weak numbers three times where
   space permits and fills the rest with other numbers.
4. Earn up to three trophies based on the total number of mistakes.

Division questions always have an integer answer and keep the chosen table
number as the divisor. For example, choosing table 3 can produce `21 : 3`.

## Build

Open the project in Android Studio, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
