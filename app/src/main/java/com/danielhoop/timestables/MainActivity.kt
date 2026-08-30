package com.danielhoop.timestables

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

class MainActivity : ComponentActivity() {
    private val practiceModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    }
}

@Composable
private fun OperatorScreen(model: PracticeViewModel) {
    var showTimerDialog by remember { mutableStateOf(false) }
    var showTimerHistoryDialog by remember { mutableStateOf(false) }
    var minutesText by remember(showTimerDialog, model.timerMinutes) {
        mutableStateOf(model.timerMinutes.toString())
    }
    var timerError by remember(showTimerDialog) { mutableStateOf<String?>(null) }

    GrayPage {
        Text("What would you like to practice?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(42.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            OperatorButton("·") { model.chooseOperator(MathOperator.MULTIPLY) }
            OperatorButton(":") { model.chooseOperator(MathOperator.DIVIDE) }
        }
        Spacer(Modifier.height(34.dp))
        Button(onClick = { showTimerDialog = true }) {
            Text("⏱  Timer", fontSize = 21.sp)
        }
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
                            Text(
                                text = "${formatHistoryDate(entry.finishedAtMillis)}, " +
                                    "${entry.durationMinutes} min",
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
        modifier = Modifier.size(126.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(symbol, fontSize = 64.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(model: PracticeViewModel) {
    Scaffold(
        containerColor = DefaultGray,
        topBar = { BackBar(model::goBack) },
    ) { padding ->
        if (model.isConfigurationLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
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
                text = if (model.operator == MathOperator.MULTIPLY) "Choose a times table" else "Choose a division table",
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
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(7.dp),
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
            if (model.operator == MathOperator.MULTIPLY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("3 ↔ 6", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Without 1 and 10", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Auto enter", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = model.autoEnter,
                    onCheckedChange = model::updateAutoEnter,
                )
            }
        }
    }
}

@Composable
private fun PracticeScreen(model: PracticeViewModel) {
    val calculation = model.currentCalculation ?: return
    var showGreen by remember { mutableStateOf(false) }
    val isWrong = model.wrongDialogCalculation != null
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
                    "${model.calculationNumber} / ${model.highestNumberText.toInt() * 2}",
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
                enabled = !isWrong,
                onDigitEntered = model::onAnswerDigitEntered,
                onSubmit = model::submitAnswer,
            )
            Spacer(Modifier.weight(1.35f))
        }
    }

    if (model.showTimeUpDialog) {
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
    } else model.wrongDialogCalculation?.let { wrongCalculation ->
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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(questionNumber) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    OutlinedTextField(
        value = answer,
        onValueChange = { value ->
            if (value.all(Char::isDigit)) {
                val digitWasAdded = value.length > answer.length
                answer = value
                if (digitWasAdded) onDigitEntered()
                if (autoEnter && value.length == expectedAnswer.toString().length) {
                    onSubmit(value)
                }
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
        keyboardActions = KeyboardActions(onDone = { onSubmit(answer) }),
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
    SimpleDateFormat("EEE, yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMillis))

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
