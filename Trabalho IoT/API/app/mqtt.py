import threading
import time
import json
from datetime import datetime

from paho.mqtt import client as mqtt_client
from sqlalchemy.orm import Session

from .database import SessionLocal
from .models import Leitura, LogAcionamento, State

# Configurações do broker
MQTT_BROKER = "test.mosquitto.org"
MQTT_PORT = 1883
MQTT_CLIENT_ID = f"api_consumer_{int(time.time())}"
MQTT_TOPICS = [
    "spvg/casa/cozinha/gas/leitura/+",
    "spvg/casa/cozinha/gas/comando/+",
    "spvg/casa/cozinha/gas/status/+",
]

# Variáveis auxiliares
last_state_by_mac = {}
last_manual_command_by_mac = {}

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Conectado ao MQTT Broker!")
        for topic in MQTT_TOPICS:
            client.subscribe(topic)
            print(f"Subscribed to {topic}")
    else:
        print(f"Falha na conexão MQTT, código {rc}")

def extract_mac(topic: str) -> str:
    return topic.split("/")[-1]

def on_message(client, userdata, msg):
    topic = msg.topic
    payload = msg.payload.decode()
    mac = extract_mac(topic)

    try:
        data = json.loads(payload)
    except json.JSONDecodeError:
        print(f"JSON inválido recebido: {payload}")
        return

    timestamp = datetime.utcnow()
    db: Session = SessionLocal()
    try:
        # Leitura
        if topic.startswith("spvg/casa/cozinha/gas/leitura/"):
            leitura = Leitura(
                mac=mac,
                timestamp=timestamp,
                gas=data["gas"],
                temperature=data["temp"],
                pressure=data["press"]
            )
            db.add(leitura)
            db.commit()
            print(f"Leitura registrada para {mac}")

        # Comando manual (armazenado apenas em cache temporário)
        elif topic.startswith("spvg/casa/cozinha/gas/comando/"):
            act = data.get("act")
            trigger = data.get("type")

            if trigger == "manual" and act in ("OPEN", "CLOSE"):
                last_manual_command_by_mac[mac] = {
                    "state": act,
                    "timestamp": timestamp
                }
                print(f"Comando manual registrado em cache: {act} ({mac})")

        # Status
        elif topic.startswith("spvg/casa/cozinha/gas/status/"):
            estado_atual = data.get("state")
            if estado_atual not in ("OPEN", "CLOSE"):
                print(f"Estado inválido: {estado_atual}")
                return

            estado_cached = last_state_by_mac.get(mac)

            if estado_cached is None:
                ultimo_log = (
                    db.query(LogAcionamento)
                    .filter_by(mac=mac)
                    .order_by(LogAcionamento.timestamp.desc())
                    .first()
                )
                if ultimo_log:
                    estado_cached = ultimo_log.state.value
                    last_state_by_mac[mac] = estado_cached

            if estado_atual != estado_cached:
                manual_cmd = last_manual_command_by_mac.get(mac)
                if manual_cmd:
                    print(f"Verificando cache manual para {mac}: esperado={manual_cmd['state']}, recebido={estado_atual}")
                    if manual_cmd["state"] == estado_atual:
                        print(f"Comando manual confirmado: {estado_atual} ({mac})")
                        del last_manual_command_by_mac[mac]
                    else:
                        print(f"Comando manual não confere: {manual_cmd['state']} ≠ {estado_atual}")

                log = LogAcionamento(
                    mac=mac,
                    timestamp=timestamp,
                    state=State(estado_atual)
                )
                db.add(log)
                db.commit()
                last_state_by_mac[mac] = estado_atual
                print(f"Log de status registrado para {mac}")
            else:
                print(f"Estado {estado_atual} repetido para {mac}, ignorado")

    except Exception as e:
        print(f"Erro ao processar mensagem: {e}")
        db.rollback()
    finally:
        db.close()

def _mqtt_thread():
    client = mqtt_client.Client(MQTT_CLIENT_ID)
    client.on_connect = on_connect
    client.on_message = on_message

    client.connect(MQTT_BROKER, MQTT_PORT)
    client.loop_forever()

def start_mqtt_client():
    thread = threading.Thread(target=_mqtt_thread, daemon=True)
    thread.start()
    print("MQTT client iniciado em background")
