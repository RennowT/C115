#include <Arduino.h>
#include <Wire.h>
#include <PubSubClient.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/semphr.h>
#include "config.h"  // WIFI_SSID, WIFI_PASS, MQTT_SERVER, MQTT_PORT, DEVICE_MAC, RELAY_PIN

// -------------------------
// Model (M)
// -------------------------
enum class ValveCommand { OPEN, CLOSE };

// -------------------------
// Abstraction (A)
// -------------------------
class IRelayDriver {
public:
    virtual ~IRelayDriver() = default;
    virtual void begin() = 0;
    virtual void openValve() = 0;
    virtual void closeValve() = 0;
};

class RelayDriver : public IRelayDriver {
public:
    RelayDriver(uint8_t pin) : _pin(pin) {}
    void begin() override {
        pinMode(_pin, OUTPUT);
        digitalWrite(_pin, HIGH); // válvula aberta
    }
    void openValve() override {
    digitalWrite(_pin, HIGH);
    Serial.printf("RelayDriver::openValve() -> pin %d HIGH, read=%d\n",
                  _pin, digitalRead(_pin));
}
void closeValve() override {
    digitalWrite(_pin, LOW);
    Serial.printf("RelayDriver::closeValve() -> pin %d LOW, read=%d\n",
                  _pin, digitalRead(_pin));
}
private:
    uint8_t _pin;
};

class IMqttService {
public:
    virtual ~IMqttService() = default;
    virtual void begin(const char* server, uint16_t port) = 0;
    virtual bool reconnect() = 0;
    virtual void loop() = 0;
    virtual void subscribeCommandTopic() = 0;
    virtual void publishStatus(const char* topic, const char* msg) = 0;
};

// -------------------------
// Service (S)
// -------------------------
class MqttService : public IMqttService {
public:
    MqttService(Client& client, const char* clientId)
      : _mqtt(client), _clientId(clientId) {}

    void begin(const char* server, uint16_t port) override {
        // resolve hostname to IP
        IPAddress ip;
        if (WiFi.hostByName(server, ip)) {
            Serial.printf("MQTT: resolvido %s -> %s\n", server, ip.toString().c_str());
            _mqtt.setServer(ip, port);
        } else {
            Serial.printf("MQTT: falha DNS para %s, usando hostname direto\n", server);
            _mqtt.setServer(server, port);
        }
        _mqtt.setCallback(callback);
    }

    bool reconnect() override {
        if (_mqtt.connected()) return true;
        if (_mqtt.connect(_clientId)) {
            subscribeCommandTopic();
            return true;
        }
        return false;
    }

    void loop() override { _mqtt.loop(); }

    void subscribeCommandTopic() override {
        char topic[80];
        snprintf(topic, sizeof(topic), "spvg/casa/cozinha/gas/comando/%s", SENSOR_MAC);
        _mqtt.subscribe(topic);
    }

    void publishStatus(const char* topic, const char* msg) override {
        if (xSemaphoreTake(_wifiSem, pdMS_TO_TICKS(1000)) == pdTRUE) {
            Serial.println("publishStatus: semáforo TAKEN");
            bool ok = _mqtt.publish(topic, msg);
            Serial.printf("publishStatus: publish() -> %s\n", ok ? "OK" : "FAIL");
            xSemaphoreGive(_wifiSem);
        } else {
            Serial.println("publishStatus: semáforo TIMEOUT, publicando mesmo assim");
            bool ok = _mqtt.publish(topic, msg);
            Serial.printf("publishStatus: publish() sem semáforo -> %s\n", ok ? "OK" : "FAIL");
        }
    }

    static void callback(char* topic, byte* payload, unsigned int length) {
        // copiar payload e terminar com '\0'
        char msgBuf[32];
        size_t msgLen = min(length, sizeof(msgBuf)-1);
        memcpy(msgBuf, payload, msgLen);
        msgBuf[msgLen] = '\0';

        Serial.printf("MQTT: recebido '%s' em %s\n", msgBuf, topic);

        // parse JSON simples: {"act":"OPEN"} ou {"act":"CLOSE"}
        ValveCommand cmd;
        if (strstr(msgBuf, "OPEN") != nullptr) {
            cmd = ValveCommand::OPEN;
        } else if (strstr(msgBuf, "CLOSE") != nullptr) {
            cmd = ValveCommand::CLOSE;
        } else {
            Serial.println("MQTT: comando desconhecido, ignorando");
            return;
        }

        // envia para a fila usando o membro estático
        if (_cmdQueue != nullptr) {
            BaseType_t ok = xQueueSend(_cmdQueue, &cmd, 0);
            if (ok != pdTRUE) {
                Serial.println("Fila cheia! comando perdido.");
            }
        }
    }
    
    static void setQueue(QueueHandle_t q) { _cmdQueue = q; }
    static void setWifiSemaphore(SemaphoreHandle_t s) { _wifiSem = s; }

private:
    PubSubClient _mqtt;
    const char* _clientId;
    static QueueHandle_t     _cmdQueue;
    static SemaphoreHandle_t _wifiSem;
};

QueueHandle_t     MqttService::_cmdQueue = nullptr;
SemaphoreHandle_t MqttService::_wifiSem   = nullptr;

// -------------------------
// Logic (L)
// -------------------------
class ValveLogic {
public:
    ValveLogic(IRelayDriver* driver)
      : _driver(driver), _state(ValveCommand::OPEN) {}

    void begin() {
        _driver->begin();
    }

    void setStatusQueue(QueueHandle_t q) {
        _statusQueue = q;
    }

    void handleCommand(const ValveCommand& cmd) {
        // acionamento do relé
        if (cmd == ValveCommand::OPEN) {
            _driver->openValve();
            _state = ValveCommand::OPEN;
        } else {
            _driver->closeValve();
            _state = ValveCommand::CLOSE;
        }

        // Enfileira o novo estado para a TaskStatusPublish
        if (_statusQueue  != nullptr) {
            xQueueSend(_statusQueue , &_state, 0);
        }
    }

private:
    IRelayDriver*    _driver;
    ValveCommand     _state;
    QueueHandle_t    _statusQueue = nullptr;
};

// -------------------------
// Application (A)
// -------------------------
QueueHandle_t     xQueueCommands;
QueueHandle_t     xQueueActuator;
QueueHandle_t     xQueueStatus;
SemaphoreHandle_t xSemaphoreWiFi;

static RelayDriver relay(RELAY_PIN);
static WiFiClient  wifiClient;
static char        clientId[24];
static MqttService mqttSrv(wifiClient, clientId);
static ValveLogic  logic(&relay);

// Prototypes
void TaskMQTTSubscribe(void* pv);
void TaskActuator(void* pv);
void TaskStatusPublish(void* pv);

// -------------------------
// Task: MQTT Subscribe
// -------------------------
void TaskMQTTSubscribe(void* pv) {
    // Recebe instância de MqttService e fila
    MqttService* mqtt = &mqttSrv;
    ValveCommand cmd;

    // Garante que a reconexão inicial ocorra
    while (!mqtt->reconnect()) {
        vTaskDelay(pdMS_TO_TICKS(2000));
    }
    mqtt->subscribeCommandTopic();

    for (;;) {
        // Mantém client loop para receber callbacks
        mqtt->loop();
        // Aqui o callback deve ter enviado cmd para fila
        if (xQueueReceive(xQueueCommands, &cmd, 0) == pdTRUE) {
            // repassar direto para TaskActuator
            xQueueSend(xQueueActuator, &cmd, 0);
        }
        vTaskDelay(pdMS_TO_TICKS(100)); // evita busy-wait
    }
}

// -------------------------
// Task: Actuator
// -------------------------
void TaskActuator(void* pv) {
    ValveCommand cmd;
    for (;;) {
        if (xQueueReceive(xQueueActuator, &cmd, portMAX_DELAY) == pdTRUE) {
            // ação imediata ao receber
            logic.handleCommand(cmd);
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// -------------------------
// Task: Status Publish
// -------------------------
void TaskStatusPublish(void* pvParameters) {
    IMqttService* mqtt = &mqttSrv;
    ValveCommand newState;

    for (;;) {
        if (xQueueReceive(xQueueStatus, &newState, portMAX_DELAY) == pdTRUE) {
            const char* stateStr = (newState == ValveCommand::OPEN) ? "{\"state\":\"OPEN\"}" : "{\"state\":\"CLOSE\"}";
            Serial.printf("TaskStatusPublish: evento recebido -> %s\n", stateStr);

            mqtt->loop();  

            bool okReconnect = mqtt->reconnect();
            Serial.printf("TaskStatusPublish: reconnect() -> %s\n",
                          okReconnect ? "SUCESSO" : "FALHOU");

            char topic[80];
            snprintf(topic, sizeof(topic),
                     "spvg/casa/cozinha/gas/status/%s",
                     DEVICE_MAC);
            Serial.printf("TaskStatusPublish: publicando em %s\n", topic);

            mqtt->publishStatus(topic, stateStr);
            Serial.println("TaskStatusPublish: publishStatus() chamado");

            mqtt->loop();
        }
    }
}

void setup() {
    Serial.begin(115200);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    // Aguarda conexão Wi-Fi antes de resolver DNS
    Serial.print("WiFi: conectando");
    while (WiFi.status() != WL_CONNECTED) {
        Serial.print('.');
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    Serial.println(" WiFi conectado");

    // Recursos FreeRTOS
    xQueueCommands    = xQueueCreate(5, sizeof(ValveCommand));
    xQueueActuator    = xQueueCreate(5, sizeof(ValveCommand));
    xQueueStatus      = xQueueCreate(5, sizeof(ValveCommand));
    xSemaphoreWiFi = xSemaphoreCreateBinary();
    MqttService::setQueue(xQueueCommands);
    MqttService::setWifiSemaphore(xSemaphoreWiFi);

    xSemaphoreGive(xSemaphoreWiFi);

    // Inicializa cliente MQTT após Wi-Fi ativo
    snprintf(clientId, sizeof(clientId), "%s-CLI", DEVICE_MAC);
    mqttSrv.begin(MQTT_SERVER, MQTT_PORT);

    // Lógica
    logic.setStatusQueue(xQueueStatus);
    logic.begin();

    // cria tasks
    xTaskCreate(TaskMQTTSubscribe, "TaskMQTTSubscribe", 4096, nullptr, 2, nullptr);
    xTaskCreate(TaskActuator, "TaskActuator", 2048, nullptr, 2, nullptr);
    xTaskCreate(TaskStatusPublish, "TaskStatusPublish", 2048, nullptr, 1, nullptr);
}

void loop() {
    vTaskDelay(portMAX_DELAY);
}
