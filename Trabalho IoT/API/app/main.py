import threading
import time
import sys

from fastapi import FastAPI
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from .mqtt import start_mqtt_client
from .database import engine, SessionLocal, get_db
from .routers import leituras, logs

app = FastAPI(title="SPVG API", version="0.1.0")

def check_db_connection():
    try:
        with engine.connect() as conn:
            # cria um objeto TextClause
            conn.execute(text("SELECT 1"))
        print("Conexão com o DB bem-sucedida!")
    except SQLAlchemyError as e:
        print("Falha ao conectar com o DB:", str(e))
        sys.exit(1)

@app.on_event("startup")
def startup_event():
    check_db_connection()
    start_mqtt_client()
    
app.include_router(leituras.router, prefix="/leituras", tags=["Leituras"])
app.include_router(logs.router, prefix="/logs", tags=["Logs"])

@app.get("/health", tags=["Health"])
async def health_check():
    return {"status": "ok"}