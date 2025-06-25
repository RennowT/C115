package com.spvg.appspvg

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.spvg.appspvg.ui.theme.AppSPVGTheme
import java.text.SimpleDateFormat
import java.util.*

class ActivityActuator : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mac = intent.getStringExtra("mac") ?: ""

        setContent {
            AppSPVGTheme {
                Scaffold(
                    topBar = { ActuatorTopBar(onBack = { finish() }) }
                ) { innerPadding ->
                    ActuatorContent(
                        modifier = Modifier.padding(innerPadding),
                        mac = mac
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ActuatorTopBar(onBack: () -> Unit) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
            },
            title = { Text("Actuator Details") }
        )
    }

    @Composable
    private fun ActuatorContent(
        modifier: Modifier = Modifier,
        mac: String
    ) {
        val scrollState = rememberScrollState()
        var startDate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf("") }
        var showHistory by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var logList by remember { mutableStateOf<List<LogResponse>>(emptyList()) }

        LaunchedEffect(showHistory) {
            if (showHistory) {
                isLoading = true
                try {
                    logList = ApiClient.api.getLogs(
                        mac = mac,
                        startDate = if (startDate.isBlank()) null else startDate,
                        endDate = if (endDate.isBlank()) null else endDate
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    logList = emptyList()
                } finally {
                    isLoading = false
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("MAC: $mac", style = MaterialTheme.typography.bodyLarge)
            DateSelector("Data inicial", startDate) { startDate = it }
            DateSelector("Data final", endDate) { endDate = it }

            Button(
                onClick = { showHistory = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar histórico")
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                repeat(3) {
                    LoadingCard()
                    Spacer(Modifier.height(12.dp))
                }
            } else if (showHistory && logList.isNotEmpty()) {
                val entries = logList.mapIndexed { index, log ->
                    val value = when (log.state.lowercase(Locale.getDefault())) {
                        "open" -> 1f
                        "close" -> 0f
                        else -> 0f
                    }
                    Entry(index.toFloat(), value)
                }

                ActuatorChart(
                    title = "Estado do Atuador",
                    entries = entries,
                    label = "State",
                    logList = logList
                )
            }
        }
    }

    @Composable
    private fun DateSelector(
        label: String,
        date: String,
        onDateSelected: (String) -> Unit
    ) {
        val context = LocalContext.current
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (date.isEmpty()) "Selecionar..." else date,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selectedDate = Calendar.getInstance().apply { set(y, m, d) }.time
                                onDateSelected(dateFormat.format(selectedDate))
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Divider()
        }
    }

    @Composable
    private fun LoadingCard() {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    @Composable
    fun ActuatorChart(title: String, entries: List<Entry>, label: String, logList: List<LogResponse>) {
        val inputFormat = remember {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
        val outputFormat = remember {
            SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
            }
        }

        // Ordenar logs por timestamp
        val sortedLogs = logList.sortedBy { log ->
            inputFormat.parse(log.timestamp)?.time ?: 0L
        }

        // Criar entradas com timestamps reais
        val timeEntries = sortedLogs.map { log ->
            val date = inputFormat.parse(log.timestamp) ?: Date(0)
            val value = when (log.state.lowercase(Locale.getDefault())) {
                "open" -> 1f
                "close" -> 0f
                else -> 0f
            }
            Entry(date.time.toFloat(), value)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            600
                        )
                        description.isEnabled = false
                        setTouchEnabled(true)
                        setPinchZoom(true)
                        legend.isEnabled = true
                        axisRight.isEnabled = false

                        xAxis.position = XAxis.XAxisPosition.BOTTOM

                        // CORREÇÃO PRINCIPAL: Usar ValueFormatter correto
                        xAxis.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return try {
                                    outputFormat.format(Date(value.toLong()))
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                        }

                        xAxis.labelRotationAngle = -45f
                        xAxis.granularity = 1f
                        xAxis.setAvoidFirstLastClipping(true)
                        xAxis.labelCount = 5 // Limitar número de labels para melhor legibilidade
                    }
                },
                update = { chart ->
                    val dataSet = LineDataSet(timeEntries, label).apply {
                        color = android.graphics.Color.MAGENTA
                        valueTextColor = android.graphics.Color.BLACK
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircles(true)
                        setDrawValues(false)
                        //mode = LineDataSet.Mode.STEPPED
                    }

                    chart.data = LineData(dataSet)

                    // Configurações adicionais para melhor visualização temporal
                    if (timeEntries.isNotEmpty()) {
                        chart.xAxis.axisMinimum = timeEntries.minOf { it.x }
                        chart.xAxis.axisMaximum = timeEntries.maxOf { it.x }

                        // Habilitar auto-rotação de labels
                        chart.xAxis.isGranularityEnabled = true
                    }

                    chart.invalidate()
                }
            )
        }
    }

}