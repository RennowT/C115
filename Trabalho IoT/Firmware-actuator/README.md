# Firmware do Módulo Atuador IoT

---

## Visão Geral

Firmware em C++ para ESP32, desenvolvido com PlatformIO e FreeRTOS, que:

1. Recebe ordens de abertura/fechamento de válvula via MQTT.
2. Aciona um relé 5 V para energizar (fechar circuito) ou desenergizar (abrir circuito) a válvula solenoide 127 V.
3. Publica estado atual da válvula para confirmação e monitoramento remoto.

---

## Tecnologias & Bibliotecas

* **Framework:** PlatformIO
* **RTOS:** FreeRTOS
* **Core:** Arduino.h
* **MQTT:** PubSubClient

---

## Estrutura de Tasks FreeRTOS

| Task Name           | Prioridade | Função                                                                                                                                           | Periodicidade       |
| ------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------- |
| `TaskMQTTSubscribe` | 2          | - Mantém conexão com broker MQTT<br>- Subscreve em `spvg/casa/cozinha/gas/comando/{MAC}`<br>- Em callback, envia comando (`OPEN`/`CLOSE`) à fila | Inicial + loop      |
| `TaskActuator`      | 2          | - Consome comandos da fila<br>- Se `OPEN`, mantém relé desligado (válvula aberta)<br>- Se `CLOSE`, aciona relé (válvula fechada)                 | Imediato ao receber |
| `TaskStatusPublish` | 1          | - Sempre que a válvula mudar de estado, publica `"OPEN"` ou `"CLOSE"` em `spvg/casa/cozinha/gas/status/{MAC}`                                    | Sob evento          |

### Filas e Estruturas

* **QueueHandle\_t xQueueCommands;**
  Armazena enum `ValveCommand { OPEN, CLOSE }` repassado de `TaskMQTTSubscribe` para `TaskActuator`.

* **SemaphoreHandle\_t xSemaphoreWiFi;**
  Garante que o publish MQTT só ocorra quando houver conexão Wi-Fi ativa.

---

## Configuração de Conexão & Hardware

1. **Wi-Fi**: SSID e senha definidos em `config.h`.
2. **MQTT**: Broker, porta e credenciais em `config.h`.
3. **Tópicos MQTT**

   * Comando:\*\* `spvg/casa/cozinha/gas/comando/{MAC}`
   * Status: \*\*`spvg/casa/cozinha/gas/status/{MAC}`
4. **GPIOs**

   * **RELAY\_PIN** → GPIO13
