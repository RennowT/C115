package com.spvg.appspvg

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManager(
    context: Context,
    private val serverUri: String = "tcp://test.mosquitto.org:1883",
    clientId: String = MqttClient.generateClientId()
) {
    private val client = MqttAndroidClient(context, serverUri, clientId)

    /** Callback acionado quando uma nova mensagem chega */
    var onMessageReceived: ((topic: String, payload: ByteArray) -> Unit)? = null

    /** Callback acionado em eventos de conexão/perda de conexão */
    var onConnectionLost: ((cause: Throwable?) -> Unit)? = null
    var onConnectComplete: ((reconnect: Boolean, serverURI: String) -> Unit)? = null

    init {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                onConnectComplete?.invoke(reconnect, serverURI)
            }

            override fun connectionLost(cause: Throwable?) {
                onConnectionLost?.invoke(cause)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                onMessageReceived?.invoke(topic, message.payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {

            }
        })
    }

    /** Conecta ao broker */
    fun connect(
        cleanSession: Boolean = true,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable?) -> Unit)? = null
    ) {
        val opts = MqttConnectOptions().apply {
            isCleanSession = cleanSession
        }
        client.connect(opts, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                onSuccess?.invoke()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                onFailure?.invoke(exception)
            }
        })
    }

    /** Inscreve em um tópico */
    fun subscribe(
        topic: String,
        qos: Int = 0,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable?) -> Unit)? = null
    ) {
        client.subscribe(topic, qos, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                onSuccess?.invoke()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                onFailure?.invoke(exception)
            }
        })
    }

    /** Publica no tópico */
    fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int = 0,
        retained: Boolean = false,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable?) -> Unit)? = null
    ) {
        val message = MqttMessage(payload).apply {
            this.qos = qos
            isRetained = retained
        }
        client.publish(topic, message, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                onSuccess?.invoke()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                onFailure?.invoke(exception)
            }
        })
    }

    /** Desconecta e libera recursos */
    fun disconnect(onComplete: (() -> Unit)? = null) {
        client.unregisterResources()
        client.disconnect(null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                onComplete?.invoke()
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                onComplete?.invoke() // mesmo se falhar, vamos liberar
            }
        })
    }
}