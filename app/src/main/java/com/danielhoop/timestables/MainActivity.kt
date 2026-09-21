package com.danielhoop.timestables

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DefaultGray = Color(0xFFD4D4D4)
private val MildGreen = Color(0xFFB8E6C0)
private val MildRed = Color(0xFFF2B8B5)
private val Purple = Color(0xFF6352C7)
private val ConfigurationLabelFontSize = 18.sp
private const val AUTO_ENTER_VISIBILITY_MILLIS = 80L

class MainActivity : ComponentActivity() {
    private val practiceModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Purple,
                    background = DefaultGray,
                    surface = DefaultGray,
                ),
            ) {
                TimesTablesApp(practiceModel)
            }
        }
    }

    override fun onStop() {
        practiceModel.pauseTimerForBackground()
        super.onStop()
    }
}

@Composable
fun TimesTablesApp(model: PracticeViewModel = viewModel()) {
    BackHandler(enabled = model.screen != AppScreen.OPERATOR) { model.goBack() }

    when (model.screen) {
        AppScreen.OPERATOR -> OperatorScreen(model)
        AppScreen.SETUP -> SetupScreen(model)
        AppScreen.PRACTICE -> PracticeScreen(model)
        AppScreen.RESULTS -> ResultsScreen(model.errorCount, model::returnToSetup)
        AppScreen.SETTINGS -> SettingsScreen(model)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperatorScreen(model: PracticeViewModel) {
    var showTimerDialog by remember { mutableStateOf(false) }
    var showTimerHistoryDialog by remember { mutableStateOf(false) }
    var showSettingsPinDialog by remember { mutableStateOf(false) }
    var pinText by remember(showSettingsPinDialog) { mutableStateOf("") }
    var repeatPinText by remember(showSettingsPinDialog) { mutableStateOf("") }
    var pinError by remember(showSettingsPinDialog) { mutableStateOf<String?>(null) }
    val pinFocusRequester = remember { FocusRequester() }
    var minutesText by remember(showTimerDialog, model.timerMinutes) {
        mutableStateOf(model.timerMinutes.toString())
    }
    var timerError by remember(showTimerDialog) { mutableStateOf<String?>(null) }

    GrayPage {
        Text("What would you like to practice?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(42.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperatorButton("+") { model.chooseOperator(MathOperator.ADDITION) }
                OperatorButton("-") { model.chooseOperator(MathOperator.SUBTRACTION) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperatorButton("×") { model.chooseOperator(MathOperator.MULTIPLY) }
                OperatorButton("÷") { model.chooseOperator(MathOperator.DIVIDE) }
            }
            Row {
                OperatorButton("×÷") { model.chooseOperator(MathOperator.MIXED) }
            }
        }
        Spacer(Modifier.height(34.dp))
        Button(onClick = { showTimerDialog = true }) {
            Text("⏱  Timer", fontSize = 21.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showSettingsPinDialog = true },
            enabled = model.settingsPinLoaded,
        ) {
            Text("\u2699  Settings", fontSize = 21.sp)
        }
    }

    if (showSettingsPinDialog) {
        val creatingPin = model.settingsPin == null
        AlertDialog(
            onDismissRequest = { showSettingsPinDialog = false },
            containerColor = DefaultGray,
            title = { Text(if (creatingPin) "Create settings PIN" else "Settings PIN") },
            text = {
                LaunchedEffect(Unit) {
                    delay(100)
                    pinFocusRequester.requestFocus()
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) {
                                pinText = value
                                if (!creatingPin && value.isNotEmpty() && value == model.settingsPin) {
                                    showSettingsPinDialog = false
                                    model.openSettings()
                                }
                            }
                            pinError = null
                        },
                        label = { Text(if (creatingPin) "PIN" else "Enter PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.focusRequester(pinFocusRequester),
                        singleLine = true,
                    )
                    if (creatingPin) {
                        OutlinedTextField(
                            value = repeatPinText,
                            onValueChange = { value ->
                                if (value.all(Char::isDigit)) repeatPinText = value
                                pinError = null
                            },
                            label = { Text("Repeat PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                    pinError?.let { Text(it, color = Color(0xFF9B2226)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        pinText.isEmpty() -> pinError = "Enter a PIN"
                        creatingPin && pinText != repeatPinText -> pinError = "The PINs do not match"
                        !creatingPin && pinText != model.settingsPin -> pinError = "Incorrect PIN"
                        else -> {
                            if (creatingPin) model.saveSettingsPin(pinText)
                            showSettingsPinDialog = false
                            model.openSettings()
                        }
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showSettingsPinDialog = false }) { Text("Cancel") } },
        )
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = DefaultGray,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Practice timer", fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            showTimerDialog = false
                            showTimerHistoryDialog = true
                            model.loadTimerHistory()
                        },
                    ) {
                        Text("History")
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) minutesText = value
                        timerError = null
                    },
                    label = { Text("Minutes") },
                    supportingText = { timerError?.let { Text(it) } },
                    isError = timerError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = minutesText.toIntOrNull()
                        if (minutes == null || minutes !in 1..1440) {
                            timerError = "Enter a number from 1 to 1440"
                        } else {
                            model.configureTimer(minutes)
                            showTimerDialog = false
                        }
                    },
                ) {
                    Text("Start timer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showTimerHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showTimerHistoryDialog = false },
            containerColor = DefaultGray,
            title = { Text("Timer history", fontWeight = FontWeight.Bold) },
            text = {
                when {
                    model.isTimerHistoryLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    model.timerHistory.isEmpty() -> Text("No finished timers yet.")
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(model.timerHistory, key = { it.id }) { entry ->
                            val cups = historyCups(entry)
                            Text(
                                text = "${formatHistoryDate(entry.finishedAtMillis)}, " +
                                    "${entry.durationMinutes} min " +
                                    "(${entry.correctCalculations}/${entry.numberOfCalculations}" +
                                    if (cups > 0) " ${"🏆".repeat(cups)})" else ")",
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = { showTimerHistoryDialog = false }) {
                        Text("Close")
                    }
                }
            },
        )
    }
}

@Composable
private fun OperatorButton(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(96.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(symbol, fontSize = if (symbol == "×÷") 42.sp else 54.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(model: PracticeViewModel) {
    if (model.operator == MathOperator.ADDITION || model.operator == MathOperator.SUBTRACTION) {
        if (model.operator == MathOperator.ADDITION) AdditionSetupScreen(model) else SubtractionSetupScreen(model)
        return
    }
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (model.operator) {
                    MathOperator.ADDITION -> "Addition"
                    MathOperator.SUBTRACTION -> "Subtraction"
                    MathOperator.MULTIPLY -> "Multiplication"
                    MathOperator.DIVIDE -> "Division"
                    MathOperator.MIXED -> "Multiplication & Division (mixed)"
                },
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (1..12).forEach { number ->
                    Button(
                        onClick = { model.startPractice(number) },
                        enabled = model.numberIsAvailable(model.operator, number),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFF9E9E9E),
                            disabledContentColor = Color(0xFF616161),
                        ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                number.toString(),
                                modifier = Modifier.align(Alignment.Center),
                                fontSize = 23.sp,
                            )
                            val trophyCount = trophyCountForErrors(
                                model.scoresByFirstNumber[number],
                            )
                            if (trophyCount > 0) {
                                Text(
                                    text = "🏆".repeat(trophyCount),
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = model::startWildcardPractice,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(7.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "?",
                            modifier = Modifier.align(Alignment.Center),
                            fontSize = 23.sp,
                        )
                        val trophyCount = trophyCountForErrors(model.wildcardScore())
                        if (trophyCount > 0) {
                            Text(
                                text = "🏆".repeat(trophyCount),
                                modifier = Modifier.align(Alignment.TopEnd),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.highestNumberText,
                onValueChange = model::updateHighestNumber,
                label = { Text("Highest number") },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "1, 2, 3, ..., 10",
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.orderedNumbers,
                    onCheckedChange = model::updateOrderedNumbers,
                )
            }
            if (model.operator != MathOperator.DIVIDE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "3 × 6 ↔ 6 × 3",
                            fontSize = ConfigurationLabelFontSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Swap number positions randomly", fontSize = 14.sp)
                    }
                    Switch(
                        checked = model.randomFirstSecond,
                        onCheckedChange = model::updateRandomFirstSecond,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Without 1 and 10",
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.withoutOneAndTen,
                    onCheckedChange = model::updateWithoutOneAndTen,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Auto enter",
                    fontSize = ConfigurationLabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = model.autoEnter,
                    onCheckedChange = model::updateAutoEnter,
                )
            }
        }
    }
}

@Composable
private fun AdditionSetupScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Addition", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.highestInputText,
                onValueChange = model::updateHighestInput,
                label = { Text("Highest input") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = model.highestResultText,
                onValueChange = model::updateHighestResult,
                label = { Text("Highest result") },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto enter", fontSize = ConfigurationLabelFontSize, fontWeight = FontWeight.SemiBold)
                Switch(checked = model.autoEnter, onCheckedChange = model::updateAutoEnter)
            }
            Button(
                onClick = model::startAdditionPractice,
                modifier = Modifier.height(56.dp),
            ) {
                Text("Start", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun SubtractionSetupScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Subtraction", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = model.subtractionHighestInputText,
                onValueChange = model::updateSubtractionHighestInput,
                label = { Text("Highest input") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = model.subtractionLowestResultText,
                onValueChange = model::updateSubtractionLowestResult,
                label = { Text("Lowest result") },
                supportingText = { model.highestNumberError?.let { Text(it) } },
                isError = model.highestNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto enter", fontSize = ConfigurationLabelFontSize, fontWeight = FontWeight.SemiBold)
                Switch(checked = model.autoEnter, onCheckedChange = model::updateAutoEnter)
            }
            Button(
                onClick = model::startSubtractionPractice,
                modifier = Modifier.height(56.dp),
            ) {
                Text("Start", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Parental settings", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text("Addition", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = model.minimumResultText,
                onValueChange = model::updateMinimumResult,
                label = { Text("Minimum result") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text("Subtraction", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = model.subtractionMinimumInputText,
                onValueChange = model::updateSubtractionMinimumInput,
                label = { Text("Minimum input") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = model.subtractionMaximumResultText,
                onValueChange = model::updateSubtractionMaximumResult,
                label = { Text("Maximum result") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text("Multiplication", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            SettingsHintsSwitch(
                enabled = model.multiplicationShowHints,
                onEnabledChange = { model.updateShowHints(MathOperator.MULTIPLY, it) },
            )
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.multiplicationNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.MULTIPLY, number, it) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Division", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            SettingsHintsSwitch(
                enabled = model.divisionShowHints,
                onEnabledChange = { model.updateShowHints(MathOperator.DIVIDE, it) },
            )
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.divisionNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.DIVIDE, number, it) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Mixed multiplication and division", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            (1..12).forEach { number ->
                SettingsNumberRow(
                    number = number,
                    enabled = number in model.mixedNumbers,
                    onEnabledChange = { model.updateNumberAvailability(MathOperator.MIXED, number, it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsHintsSwitch(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Show hints", fontSize = 19.sp)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun SettingsNumberRow(number: Int, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(number.toString(), fontSize = 19.sp)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun PracticeScreen(model: PracticeViewModel) {
    val calculation = model.currentCalculation ?: return
    val hintLines = if (model.showHintsFor(calculation)) calculation.hintLines() else null
    var showHintDialog by remember(model.calculationNumber) { mutableStateOf(false) }
    var showGreen by remember { mutableStateOf(false) }
    val isWrong = model.wrongDialogCalculation != null
    val isBlockingDialog = isWrong || model.retryCalculation != null ||
        model.showLeaveConfirmation || model.showTimeUpDialog
    val targetColor = when {
        isWrong -> MildRed
        showGreen -> MildGreen
        else -> DefaultGray
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "answer background",
    )

    LaunchedEffect(model.correctFlashSequence) {
        if (model.correctFlashSequence > 0) {
            showGreen = true
            delay(600)
            showGreen = false
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = { BackBar(model::goBack, backgroundColor) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${model.calculationNumber} / ${model.totalCalculationCount}",
                    fontSize = 16.sp,
                    color = Color.DarkGray,
                )
                if (model.timerIsArmed || model.showTimeUpDialog) {
                    Text(
                        "⏱ ${formatTimer(model.remainingTimerSeconds)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray,
                    )
                }
                if (hintLines != null && !isBlockingDialog) {
                    TextButton(
                        onClick = { showHintDialog = true },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Text("?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.weight(0.65f))
            Text(
                text = calculation.expression,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            AnswerInput(
                questionNumber = model.calculationNumber,
                expectedAnswer = calculation.expectedAnswer,
                autoEnter = model.autoEnter,
                enabled = !isBlockingDialog,
                onDigitEntered = model::onAnswerDigitEntered,
                onSubmit = model::submitAnswer,
            )
            Spacer(Modifier.weight(1.35f))
        }
    }

    if (showHintDialog && !isBlockingDialog) {
        AlertDialog(
            onDismissRequest = { showHintDialog = false },
            containerColor = DefaultGray,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    hintLines?.forEach { line ->
                        if (line.isBlank()) {
                            Spacer(Modifier.height(14.dp))
                        } else {
                            Text(
                                text = line,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 19.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = { showHintDialog = false }) {
                        Text("OK")
                    }
                }
            },
        )
    } else if (model.showTimeUpDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏱", fontSize = 54.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text("🥳", fontSize = 72.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = model::acknowledgeTimeUp) {
                        Text("Yay! 😊", fontSize = 20.sp)
                    }
                }
            },
        )
    } else if (model.showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = model::continuePractice,
            containerColor = DefaultGray,
            title = { Text("Leave this set?", fontWeight = FontWeight.Bold) },
            text = { Text("Your current progress in this set will be lost.") },
            confirmButton = {
                Button(onClick = model::continuePractice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continue")
                        CalculatorIcon()
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = model::confirmLeavePractice) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Leave")
                        DoorIcon()
                    }
                }
            },
        )
    } else if (model.wrongDialogCalculation != null) {
        val wrongCalculation = model.wrongDialogCalculation ?: return
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Text(
                    "${wrongCalculation.expression} = ${wrongCalculation.expectedAnswer}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = model::acceptCorrectAnswer) {
                        Text("OK 😊", fontSize = 18.sp)
                    }
                }
            },
        )
    } else if (model.retryCalculation != null) {
        val retryCalculation = model.retryCalculation ?: return
        AlertDialog(
            onDismissRequest = {},
            containerColor = DefaultGray,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        retryCalculation.expression,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 40.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    AnswerInput(
                        questionNumber = model.retryPromptSequence,
                        expectedAnswer = retryCalculation.expectedAnswer,
                        autoEnter = model.autoEnter,
                        enabled = true,
                        onDigitEntered = model::onAnswerDigitEntered,
                        onSubmit = model::submitRetryAnswer,
                    )
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun AnswerInput(
    questionNumber: Int,
    expectedAnswer: Int,
    autoEnter: Boolean,
    enabled: Boolean,
    onDigitEntered: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var answer by remember(questionNumber) { mutableStateOf("") }
    var answerWasSubmitted by remember(questionNumber) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitCurrentAnswer() {
        if (answer.isNotEmpty() && !answerWasSubmitted) {
            answerWasSubmitted = true
            onSubmit(answer)
        }
    }

    LaunchedEffect(questionNumber) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Let Compose render the final digit once before auto-enter moves to the
    // next question. Without this small delay, one-digit correct answers such
    // as 9 can appear never to have been entered at all.
    LaunchedEffect(autoEnter, answer, expectedAnswer) {
        if (autoEnter &&
            !answerWasSubmitted &&
            answer.length == expectedAnswer.toString().length
        ) {
            delay(AUTO_ENTER_VISIBILITY_MILLIS)
            submitCurrentAnswer()
        }
    }

    OutlinedTextField(
        value = answer,
        onValueChange = { value ->
            if (value.all(Char::isDigit)) {
                val digitWasAdded = value.length > answer.length
                answer = value
                if (digitWasAdded) onDigitEntered()
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submitCurrentAnswer() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun ResultsScreen(errorCount: Int, onContinue: () -> Unit) {
    val trophyCount = trophyCountForErrors(errorCount)
    GrayPage {
        Text("Great work!", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Text(
            if (trophyCount > 0) List(trophyCount) { "🏆" }.joinToString(" ") else "😊",
            fontSize = 62.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (errorCount == 0) "A perfect round!" else "$errorCount ${if (errorCount == 1) "mistake" else "mistakes"} this time",
            fontSize = 21.sp,
        )
        Spacer(Modifier.height(42.dp))
        Button(onClick = onContinue, modifier = Modifier.height(56.dp)) {
            Text("Choose another table", fontSize = 19.sp)
        }
    }
}

private fun trophyCountForErrors(errorCount: Int?): Int = when (errorCount) {
    0 -> 3
    1 -> 2
    2 -> 1
    else -> 0
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatHistoryDate(timestampMillis: Long): String =
    SimpleDateFormat("EEE", Locale.getDefault())
        .format(Date(timestampMillis))
        .trimEnd('.') + "., " +
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMillis))

private fun historyCups(entry: TimerHistoryEntry): Int {
    if (entry.numberOfCalculations == 0) return 0
    val percentage = entry.correctCalculations * 100.0 / entry.numberOfCalculations
    return when {
        percentage >= 98.0 -> 3
        percentage >= 95.0 -> 2
        percentage >= 90.0 -> 1
        else -> 0
    }
}

@Composable
private fun CalculatorIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        drawRoundRect(
            color = color,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 6.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), 6.dp.toPx()),
            strokeWidth = 1.8.dp.toPx(),
        )
        listOf(5f to 10f, 10f to 10f, 5f to 14f, 10f to 14f).forEach { (x, y) ->
            drawCircle(color = color, radius = 1.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x.dp.toPx(), y.dp.toPx()))
        }
    }
}

@Composable
private fun DoorIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val left = 2.dp.toPx()
        val top = 2.dp.toPx()
        val bottom = size.height - 2.dp.toPx()
        val frameRight = 10.dp.toPx()

        // Flat door frame, like a conventional exit sign.
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, top), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(frameRight, top), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(frameRight, top), androidx.compose.ui.geometry.Offset(frameRight, bottom), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(frameRight, bottom), stroke.width)

        // Small escape-style walking figure moving from left to right.
        drawCircle(
            color = color,
            radius = 1.35.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 5.5.dp.toPx()),
        )
        drawLine(color, androidx.compose.ui.geometry.Offset(5.dp.toPx(), 7.dp.toPx()), androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(5.5.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(3.5.dp.toPx(), 10.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(5.5.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(8.dp.toPx(), 9.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), androidx.compose.ui.geometry.Offset(4.dp.toPx(), 14.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(6.dp.toPx(), 10.5.dp.toPx()), androidx.compose.ui.geometry.Offset(8.dp.toPx(), 14.dp.toPx()), stroke.width)

        // Direction arrow to the right of the door.
        val arrowY = 10.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(11.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(14.5.dp.toPx(), 7.5.dp.toPx()), stroke.width)
        drawLine(color, androidx.compose.ui.geometry.Offset(17.dp.toPx(), arrowY), androidx.compose.ui.geometry.Offset(14.5.dp.toPx(), 12.5.dp.toPx()), stroke.width)
    }
}

@Composable
private fun GrayPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DefaultGray)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun BackBar(onBack: () -> Unit, background: Color = DefaultGray) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(52.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Back", fontSize = 18.sp, color = Color(0xFF35268D))
        }
    }
}
