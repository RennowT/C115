#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_BMP085.h>
#include <Adafruit_SSD1306.h>
#include <MQUnifiedsensor.h>
#include <PubSubClient.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/semphr.h>
#include "config.h"

// -------------------------
// Model (M)
// -------------------------
struct SensorReading {
    float gasPPM;
    float temperature;
    float pressure;
    uint32_t timestamp;
};

// -------------------------
// Abstraction (A)
// -------------------------
class ISensorReader {
public:
    virtual ~ISensorReader() = default;
    virtual SensorReading read() = 0;
};

class IDisplay {
public:
    virtual ~IDisplay() = default;
    virtual void update(const SensorReading& data) = 0;
};

class IMqttPublisher {
public:
    virtual ~IMqttPublisher() = default;
    virtual void begin(const char* server, uint16_t port) = 0;
    virtual bool reconnect() = 0;                     
    virtual void loop() = 0;
    virtual void publish(const SensorReading& data) = 0;
    virtual void publishCommand(const char* topic, const char* msg) = 0;
};

// -------------------------
// Service (S)
// -------------------------
class SensorReader : public ISensorReader {
public:
    SensorReader(int analogPin, SemaphoreHandle_t i2cSem)
      : _pin(analogPin), _i2cSem(i2cSem) {
        // Inicializa I2C e BMP180
        Wire.begin(5, 4);
        bmp.begin();
    }

    SensorReading read() override {
        SensorReading r;

        // 1) Leitura direta do ADC (0–4095) → tensão medida (0–3.3 V)
        int raw = analogRead(_pin);
        float measuredV = raw * (3.3f / 4095.0f);

        // 2) Reconverte para tensão real no sensor (divisor 1:2)
        float sensorV = measuredV * 2.0f;  // agora entre 0 e ~2.5 V (sensor até 5 V)

        // 3) Mapeamento linear de [0…5 V] → [200…10000 PPM]
        //    PPM = (sensorV / 5.0) * (10000 – 200) + 200
        float ppm = (sensorV / 5.0f) *  (10000.0f - 200.0f) + 200.0f;

        // 4) Garante limites
        if (ppm < 200.0f)   ppm = 200.0f;
        if (ppm > 10000.0f) ppm = 10000.0f;

        r.gasPPM = ppm;

        // 5) Leitura BMP180
        if (xSemaphoreTake(_i2cSem, portMAX_DELAY) == pdTRUE) {
            r.temperature = bmp.readTemperature();
            r.pressure    = bmp.readPressure() / 100.0f;
            xSemaphoreGive(_i2cSem);
        }

        r.timestamp = millis();
        return r;
    }

private:
    int _pin;
    Adafruit_BMP085 bmp;
    SemaphoreHandle_t _i2cSem;
};

/// OledDisplay: atualiza display SSD1306 via I2C
class OledDisplay : public IDisplay {
public:
    OledDisplay(SemaphoreHandle_t i2cSem)
      : _display(128, 64, &Wire), _i2cSem(i2cSem) {
        if (xSemaphoreTake(_i2cSem, portMAX_DELAY) == pdTRUE) {
            _display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
            _display.clearDisplay();
            _display.setTextSize(1);
            _display.setTextColor(SSD1306_WHITE);
            xSemaphoreGive(_i2cSem);
        }
    }
    void update(const SensorReading& data) override {
        if (xSemaphoreTake(_i2cSem, portMAX_DELAY) == pdTRUE) {
            _display.clearDisplay();
            _display.setCursor(0, 0);
            _display.setTextSize(2);
            _display.setTextColor(SSD1306_WHITE);
            _display.print(data.gasPPM, 1); _display.print(" ppm");
            _display.setCursor(0, 24);
            _display.print(data.temperature, 1); _display.print(" C");
            _display.setCursor(0, 48);
            _display.print(data.pressure, 1); _display.print(" hPa");
            _display.display();
            xSemaphoreGive(_i2cSem);
        }
    }
private:
    Adafruit_SSD1306 _display;
    SemaphoreHandle_t _i2cSem;
};

/// MqttPublisher: publica via PubSubClient
class MqttPublisher : public IMqttPublisher {
public:
    MqttPublisher(Client& netClient, const char* clientId)
      : _mqtt(netClient), _clientId(clientId) {}

    void begin(const char* server, uint16_t port) override {
        _mqtt.setServer(server, port);
    }

    bool reconnect() override {
        if (_mqtt.connected()) return true;
        // tenta conectar sem usuário/senha
        if (_mqtt.connect(_clientId)) {
            Serial.println("MQTT: conectado ao broker");
            return true;
        } else {
            Serial.print("MQTT: falha ao conectar, rc=");
            Serial.println(_mqtt.state());
            return false;
        }
    }

    void loop() override {
        _mqtt.loop();
    }

    void publish(const SensorReading& data) override {
        char topic[80], payload[128];
        snprintf(topic, sizeof(topic),
                 "spvg/casa/cozinha/gas/leitura/%s",
                 DEVICE_MAC);
        snprintf(payload, sizeof(payload),
                 "{\"gas\":%.1f,\"temp\":%.1f,\"press\":%.1f}",
                 data.gasPPM, data.temperature, data.pressure);
        _mqtt.publish(topic, payload);
    }

    void publishCommand(const char* topic, const char* msg) override {
        _mqtt.publish(topic, msg);
    }

private:
    PubSubClient _mqtt;
    const char*  _clientId;
};

// -------------------------
// Logic (L)
// -------------------------
class SystemLogic {
public:
    SystemLogic(ISensorReader* rdr, IDisplay* disp, IMqttPublisher* mqtt)
      : reader(rdr), display(disp), publisher(mqtt)
    {
        xQueueReadingsDisplay = xQueueCreate(10, sizeof(SensorReading));
        xQueueReadingsMqtt    = xQueueCreate(10, sizeof(SensorReading));
        xSemaphoreWiFi  = xSemaphoreCreateBinary();
        xSemaphoreI2C   = xSemaphoreCreateMutex();
    }

    QueueHandle_t getQueueDisplay() const { return xQueueReadingsDisplay; }
    QueueHandle_t getQueueMqtt()    const { return xQueueReadingsMqtt;    }
    SemaphoreHandle_t getWifiSem()  const { return xSemaphoreWiFi;         }
    SemaphoreHandle_t getI2CSem()   const { return xSemaphoreI2C;          }

    ISensorReader*  reader;
    IDisplay*       display;
    IMqttPublisher* publisher;

private:
    QueueHandle_t      xQueueReadingsDisplay;
    QueueHandle_t      xQueueReadingsMqtt;
    SemaphoreHandle_t  xSemaphoreWiFi;
    SemaphoreHandle_t  xSemaphoreI2C;
};

// -------------------------
// Task: Sensor Read
// -------------------------
void TaskSensorRead(void* pvParameters) {
    auto logic = static_cast<SystemLogic*>(pvParameters);
    TickType_t lastWake = xTaskGetTickCount();
    const TickType_t interval = pdMS_TO_TICKS(5000);

    for (;;) {
        SensorReading data = logic->reader->read();

        xQueueSend(logic->getQueueDisplay(), &data, 0); // envia para display
        xQueueSend(logic->getQueueMqtt(), &data, 0); // envia para mqtt

        vTaskDelayUntil(&lastWake, interval);
    }
}

// -------------------------
// Task: Display
// -------------------------
void TaskDisplay(void* pvParameters) {
    auto logic = static_cast<SystemLogic*>(pvParameters);
    SensorReading data;
    for (;;) {
        if (xQueueReceive(logic->getQueueDisplay(), &data, portMAX_DELAY) == pdTRUE) {
            logic->display->update(data);
        }
    }
}

// -------------------------
// Task: MQTT Publish
// -------------------------
void TaskMQTTPublish(void* pvParameters) {
    auto logic = static_cast<SystemLogic*>(pvParameters);
    SensorReading data;
    char cmdTopic[80];

    for (;;) {
        // 1) Aguarda nova leitura
        if (xQueueReceive(logic->getQueueMqtt(), &data, portMAX_DELAY) == pdTRUE) {
            Serial.printf("MQTT  : GAS=%.1fppm T=%.1fC P=%.1fhPa\n",
                          data.gasPPM, data.temperature, data.pressure);

            // 2) Aguarda indefinidamente o Wi-Fi sinalizar conectividade
            xSemaphoreTake(logic->getWifiSem(), portMAX_DELAY);

            // 3) Loop de (re)conexão MQTT
            while (!logic->publisher->reconnect()) {
                Serial.println("MQTT: aguardando broker...");
                vTaskDelay(pdMS_TO_TICKS(2000));
            }

            // 4) Publica leitura
            logic->publisher->publish(data);

            // 5) Publica comando de segurança
            snprintf(cmdTopic, sizeof(cmdTopic),
                     "spvg/casa/cozinha/gas/comando/%s",
                     DEVICE_MAC);
            const char* cmd = (data.gasPPM > GAS_LEAK_THRESHOLD_PPM)
                              ? "{\"act\":\"CLOSE\"}"
                              : "{\"act\":\"OPEN\"}";
            logic->publisher->publishCommand(cmdTopic, cmd);

            // 6) Mantém o keep-alive e libera o semáforo pra próxima publicação
            logic->publisher->loop();
            xSemaphoreGive(logic->getWifiSem());
        }
    }
}

// -------------------------
// Application (A)
// -------------------------
static SystemLogic* logicPtr;

void setup() {
    Serial.begin(115200);
    delay(1000);

    // Conecta Wi-Fi
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    // Inicializa lógica e semáforos
    // Cria mutex I2C antes de instanciar serviços
    static SystemLogic logic(
        nullptr, nullptr, nullptr
    );
    logicPtr = &logic;

    // Instancia serviços
    static SensorReader sensor(MQ6_PIN, logicPtr->getI2CSem());
    static OledDisplay  oled(logicPtr->getI2CSem());

    // Cria um WiFiClient nomeado e passa-o ao construtor
    static WiFiClient    espClient;
    static char clientId[32];
    snprintf(clientId, sizeof(clientId), "%s-%04X", DEVICE_MAC, esp_random() & 0xFFFF);
    static MqttPublisher mqtt(espClient, clientId);
    mqtt.begin(MQTT_SERVER, MQTT_PORT);

    // Atualiza ponteiros de reader/display/mqtt
    logicPtr->reader    = &sensor;
    logicPtr->display   = &oled;
    logicPtr->publisher = &mqtt;

    // Cria TaskSensorRead (Prioridade 2)
    xTaskCreate(
        TaskSensorRead,
        "TaskSensorRead",
        4096,
        logicPtr,
        2,
        nullptr
    );
    // Cria TaskDisplay (Prioridade 1)
    xTaskCreate(
        TaskDisplay,
        "TaskDisplay",    
        4096, 
        logicPtr, 
        1, 
        nullptr
    );
    // Cria TaskMQTTPublish (Prioridade 2)
    xTaskCreate(
        TaskMQTTPublish,
        "TaskMQTTPublish",
        4096,
        logicPtr,
        2,
        nullptr
    );
}

void loop() {
    // Gerencia semáforo de Wi-Fi/MQTT
    if (WiFi.status() == WL_CONNECTED) {
        xSemaphoreGive(logicPtr->getWifiSem());
    }
    // Aguarda antes de verificar novamente
    vTaskDelay(pdMS_TO_TICKS(5000));
}