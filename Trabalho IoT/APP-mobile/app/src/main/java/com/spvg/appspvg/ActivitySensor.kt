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
import com.spvg.appspvg.ui.theme.AppSPVGTheme
import java.text.SimpleDateFormat
import java.util.*

class ActivitySensor : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mac = intent.getStringExtra("mac") ?: ""
        val gas = intent.getFloatExtra("gas", 0f)
        val temp = intent.getFloatExtra("temp", 0f)
        val pressure = intent.getFloatExtra("pressure", 0f)

        setContent {
            AppSPVGTheme {
                Scaffold(
                    topBar = { SensorTopBar(onBack = { finish() }) }
                ) { innerPadding ->
                    SensorContent(
                        modifier = Modifier.padding(innerPadding),
                        mac = mac,
                        gas = gas,
                        temp = temp,
                        pressure = pressure
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SensorTopBar(onBack: () -> Unit) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
            },
            title = { Text("Sensor Details") }
        )
    }

    @Composable
    private fun SensorContent(
        modifier: Modifier = Modifier,
        mac: String,
        gas: Float,
        temp: Float,
        pressure: Float
    ) {
        val scrollState = rememberScrollState()
        var startDate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf("") }
        var showHistory by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var leituraList by remember { mutableStateOf<List<LeituraResponse>>(emptyList()) }

        LaunchedEffect(showHistory) {
            if (showHistory) {
                isLoading = true
                try {
                    leituraList = ApiClient.api.getLeituras(
                        mac = mac,
                        startDate = if (startDate.isBlank()) null else startDate,
                        endDate = if (endDate.isBlank()) null else endDate
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    leituraList = emptyList()
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
            SensorDetails(mac, gas, temp, pressure)
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
            } else if (showHistory && leituraList.isNotEmpty()) {
                SensorChart("Gás", leituraList, { it.gas }, "ppm")
                Spacer(Modifier.height(16.dp))
                SensorChart("Temperatura", leituraList, { it.temperature }, "°C")
                Spacer(Modifier.height(16.dp))
                SensorChart("Pressão", leituraList, { it.pressure }, "hPa")

            }
        }
    }

    @Composable
    private fun SensorDetails(mac: String, gas: Float, temp: Float, pressure: Float) {
        Text("MAC: $mac", style = MaterialTheme.typography.bodyLarge)
        Text("Gás: ${"%.1f".format(gas)} ppm", style = MaterialTheme.typography.bodyLarge)
        Text("Temperatura: ${"%.1f".format(temp)} °C", style = MaterialTheme.typography.bodyLarge)
        Text("Pressão: ${"%.1f".format(pressure)} hPa", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
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
    fun SensorChart(title: String, leituraList: List<LeituraResponse>, valueSelector: (LeituraResponse) -> Float, label: String) {
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

        val sortedList = leituraList.sortedBy { leitura ->
            inputFormat.parse(leitura.timestamp) ?: Date(0)
        }

        val entries = sortedList.map { leitura ->
            val date = inputFormat.parse(leitura.timestamp) ?: Date(0)
            val millis = date.time.toFloat()
            Entry(millis, valueSelector(leitura))
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
                        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
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
                        xAxis.labelCount = 5
                    }
                },
                update = { chart ->
                    val dataSet = LineDataSet(entries, label).apply {
                        color = android.graphics.Color.BLUE
                        valueTextColor = android.graphics.Color.BLACK
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircles(true)
                        setDrawValues(false)
                    }

                    chart.data = LineData(dataSet)
                    chart.invalidate()
                }
            )
        }
    }

}
