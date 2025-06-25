package com.spvg.appspvg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spvg.appspvg.ui.theme.AppSPVGTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val sensorMac   = "0C:B8:15:F6:82:8C"
    private val actuatorMac = "A8:42:E3:91:18:1C"

    private var gasReading      by mutableStateOf(0f)
    private var tempReading     by mutableStateOf(0f)
    private var pressureReading by mutableStateOf(0f)
    private var valveOpen       by mutableStateOf(false)

    private lateinit var mqtt: MqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mqtt = MqttManager(this).apply {
            onConnectComplete = { _, _ ->
                subscribe("spvg/casa/cozinha/gas/leitura/$sensorMac")
                subscribe("spvg/casa/cozinha/gas/status/$actuatorMac")
            }
            onMessageReceived = { topic, payload ->
                val msg = String(payload)
                when {
                    topic.endsWith("/leitura/$sensorMac") -> {
                        try {
                            val json = JSONObject(msg)
                            gasReading      = json.optDouble("gas",     gasReading.toDouble()).toFloat()
                            tempReading     = json.optDouble("temp",    tempReading.toDouble()).toFloat()
                            pressureReading = json.optDouble("press",   pressureReading.toDouble()).toFloat()
                        } catch (_: Exception) {
                            msg.toFloatOrNull()?.let { gasReading = it }
                        }
                    }
                    topic.endsWith("/status/$actuatorMac") -> {
                        try {
                            val state = JSONObject(msg).getString("state")
                            valveOpen = state.equals("OPEN", ignoreCase = true)
                        } catch (_: Exception) { }
                    }
                }
            }
            connect(onSuccess = {}, onFailure = {})
        }

        setContent {
            AppSPVGTheme {
                Scaffold { innerPadding ->
                    val ctx = LocalContext.current
                    Column(
                        Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SensorCard(
                            mac = sensorMac,
                            gas = gasReading,
                            temperature = tempReading,
                            pressure = pressureReading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // abre a ActivitySensor passando os dados atuais
                                    val intent = Intent(ctx, ActivitySensor::class.java).apply {
                                        putExtra("mac", sensorMac)
                                        putExtra("gas", gasReading)
                                        putExtra("temp", tempReading)
                                        putExtra("pressure", pressureReading)
                                    }
                                    ctx.startActivity(intent)
                                }
                        )
                        ActuatorCard(
                            mac = actuatorMac,
                            isOpen = valveOpen,
                            onToggle = { open ->
                                valveOpen = open
                                val cmdJson = JSONObject().put("act", if (open) "OPEN" else "CLOSE").toString()
                                mqtt.publish(
                                    "spvg/casa/cozinha/gas/comando/$sensorMac",
                                    cmdJson.toByteArray()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(ctx, ActivityActuator::class.java).apply {
                                        putExtra("mac", actuatorMac)
                                    }
                                    ctx.startActivity(intent)
                                }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mqtt.disconnect()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    // Estes valores virão do seu ViewModel/MQTT/API
    sensorMac: String = "0C:B8:15:F6:82:8C",
    gasReading: Float = 0.0f,
    tempReading: Float = 0.0f,
    pressureReading: Float = 0.0f,
    actuatorMac: String = "A8:42:E3:91:18:1C",
    valveOpen: Boolean = false,
    onToggleValve: (Boolean) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SensorCard(
            mac = sensorMac,
            gas = gasReading,
            temperature = tempReading,
            pressure = pressureReading
        )
        ActuatorCard(
            mac = actuatorMac,
            isOpen = valveOpen,
            onToggle = onToggleValve
        )
    }
}

@Composable
fun SensorCard(
    mac: String,
    gas: Float,
    temperature: Float,
    pressure: Float,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Sensor", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("MAC: $mac", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("Gás: ${"%.1f".format(gas)} ppm", style = MaterialTheme.typography.bodyLarge)
            Text("Temperatura: ${"%.1f".format(temperature)} °C", style = MaterialTheme.typography.bodyLarge)
            Text("Pressão: ${"%.1f".format(pressure)} hPa", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ActuatorCard(
    mac: String,
    isOpen: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text("Válvula", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("MAC: $mac", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isOpen) "Estado: Aberta" else "Estado: Fechada",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onToggle(!isOpen) },
                modifier = Modifier.align(Alignment.Start),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isOpen) "Fechar" else "Abrir")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppSPVGTheme {
        MainScreen(
            sensorMac = "DE:AD:BE:EF:00:01",
            gasReading = 0.34f,
            tempReading = 26.1f,
            pressureReading = 100.7f,
            valveOpen = true,
            onToggleValve = {}
        )
    }
}
