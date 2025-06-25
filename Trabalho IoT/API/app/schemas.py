from pydantic import BaseModel
from datetime import datetime
from enum import Enum


class LeituraOut(BaseModel):
    timestamp: datetime
    gas: float
    temperature: float
    pressure: float

    class Config:
        orm_mode = True


class StateType(str, Enum):
    OPEN = "OPEN"
    CLOSE = "CLOSE"

class LogOut(BaseModel):
    timestamp: datetime
    state: StateType

    class Config:
        orm_mode = True
