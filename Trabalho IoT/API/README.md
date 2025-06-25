# API de Coleta e Consulta MQTT

Este projeto implementa uma API RESTful em Python, utilizando FastAPI, para coletar dados de leituras publicadas em um broker MQTT, armazená-los em um banco de dados MySQL e disponibilizá-los para um aplicativo móvel via HTTP. A API não publica mensagens no broker MQTT; ela apenas consome (assinante) dados de sensores e fornece endpoints para consulta de históricos, dashboards e controle remoto.

---

## Componentes do Projeto

**Backend (API REST + Banco de Dados)**

   * **FastAPI**: framework web para criar a API.
   * **MySQL**: armazena:
     * Leituras de sensores (timestamp, valor de gás, temperatura, pressão).
     * Logs de acionamento de válvula (manual vs. automático, timestamp).

---

## Endpoints Principais

### Leituras de Sensores

* `GET /leituras/{mac}`
  Retorna histórico de leituras para o dispositivo com MAC específico.

* Parâmetros opcionais: `start_date`, `end_date`.

### Logs de Acionamento

* `GET /logs/{mac}`
  Retorna histórico de acionamentos para dispositivo.

* Parâmetros opcionais: `start_date`, `end_date`.

---

## Banco de Dados

Tabelas principais:

* `leituras`:

  * `id` (PK)
  * `mac` (string)
  * `timestamp` (datetime)
  * `gas` (float)
  * `temperature` (float)
  * `pressure` (float)

* `logs`:

  * `id` (PK)
  * `mac` (string)
  * `timestamp` (datetime)
  * `triggered_by` (enum: `manual`, `automatic`)

