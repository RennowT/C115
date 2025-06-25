from sqlalchemy import Column, BigInteger, String, Float, DateTime, Enum
from .database import Base
import enum


class State(enum.Enum):
    OPEN = "OPEN"
    CLOSE = "CLOSE"

class Leitura(Base):
    __tablename__ = "leituras"

    id = Column(BigInteger, primary_key=True, index=True)
    mac = Column(String(17), nullable=False)
    timestamp = Column(DateTime(timezone=False), nullable=False)
    gas = Column(Float, nullable=False)
    temperature = Column(Float, nullable=False)
    pressure = Column(Float, nullable=False)

class LogAcionamento(Base):
    __tablename__ = "logs"

    id = Column(BigInteger, primary_key=True, index=True)
    mac = Column(String(17), nullable=False)
    timestamp = Column(DateTime(timezone=False), nullable=False)
    state = Column(Enum(State), nullable=False)
