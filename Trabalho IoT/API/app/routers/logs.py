from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime

from ..database import get_db
from ..models import LogAcionamento
from ..schemas import LogOut

router = APIRouter()

@router.get("/{mac}", response_model=List[LogOut])
def get_logs(
    mac: str,
    start_date: Optional[str] = Query(None),
    end_date: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    query = db.query(LogAcionamento).filter(LogAcionamento.mac == mac)

    if start_date:
        start = datetime.strptime(start_date, "%Y-%m-%d")
        query = query.filter(LogAcionamento.timestamp >= start)
    if end_date:
        end = datetime.strptime(end_date, "%Y-%m-%d")
        end = end.replace(hour=23, minute=59, second=59, microsecond=999999)
        query = query.filter(LogAcionamento.timestamp <= end)

    return query.order_by(LogAcionamento.timestamp.desc()).all()
