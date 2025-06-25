# Firmware do Módulo Sensor IoT

---

## Visão Geral

Firmware em C++ para ESP32, implementado com PlatformIO e FreeRTOS, que:

1. Lê concentração de GLP via sensor MQ-6 (analógico).
2. Mede temperatura e pressão com BMP180 (I²C).
3. Exibe valores em display OLED (0,96″, I²C).
4. Publica leituras pelo protocolo MQTT a cada 5 segundos.

---

## Tecnologias & Bibliotecas

* **Framework:** PlatformIO
* **RTOS:** FreeRTOS
* **Core:** Arduino.h
* **MQTT:** PubSubClient
* **I²C:** Wire.h
* **BMP180:** Adafruit\_BMP085
* **OLED:** Adafruit\_SSD1306

---

## Estrutura de Tasks FreeRTOS

| Task Name         | Prioridade | Função                                                                                                                                                                                                                                                                                                 | Periodicidade         |
| ----------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------- |
| `TaskSensorRead`  | 2          | - Lê o MQ-6 (gás GLP) e BMP180 (temperatura e pressão).<br>- Envia os dados para a fila de leitura.                                                                                                                                                                                                    | A cada 5 s            |
| `TaskDisplay`     | 1          | - Consome a fila de leitura.<br>- Atualiza o display OLED com os valores mais recentes.                                                                                                                                                                                                                | Sob demanda           |
| `TaskMQTTPublish` | 2          | - Consome a fila de leitura.<br>- Publica as leituras no tópico `spvg/casa/cozinha/gas/leitura/{MAC}`.<br>- Verifica condições de segurança (limiar de gás).<br>- Se necessário, publica comando de acionamento da válvula no tópico `spvg/casa/cozinha/gas/comando/{MAC}` (Ex.: "OPEN" ou "CLOSE"). | Imediato após leitura |


### Filas e Estruturas

* **QueueHandle_t xQueueReadingsDisplay;**  
  Armazena structs `SensorReading { float gasPPM; float temperature; float pressure; uint32_t timestamp; }` para a TaskDisplay.

* **QueueHandle_t xQueueReadingsMqtt;**  
  Armazena structs `SensorReading { float gasPPM; float temperature; float pressure; uint32_t timestamp; }` para a TaskMQTTPublish.

* **SemaphoreHandle_t xSemaphoreWiFi;**  
  Garante que o publish MQTT só ocorra quando conectado à rede.

* **SemaphoreHandle_t xSemaphoreI2C;**  
  Garante acesso exclusivo ao barramento I²C (BMP180 e OLED).

---

## Configuração de Conexão

1. **Wi-Fi**: SSID, senha definidos em `config.h`.
2. **MQTT**: Broker, porta e credenciais em `config.h`.
3. **I²C**:

   * SDA → GPIO 5
   * SCL → GPIO 4
4. **Analog Input**:

   * MQ-6 → ADC1\_CHANNEL\_0 (GPIO 36)
