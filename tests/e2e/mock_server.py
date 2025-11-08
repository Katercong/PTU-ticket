import re
import uuid
import threading
from datetime import datetime
from typing import Optional
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

app = FastAPI()

INITIAL_TICKETS = [
    {"id": 1, "fromStation": "A", "toStation": "B", "date": "2026-06-18", "price": 100.0, "stock": 50, "type": "ADULT"},
    {"id": 2, "fromStation": "A", "toStation": "B", "date": "2026-06-18", "price": 120.0, "stock": 10, "type": "CHILD"},
    {"id": 3, "fromStation": "A", "toStation": "B", "date": "2026-06-19", "price": 100.0, "stock": 5, "type": "ADULT"},
    {"id": 4, "fromStation": "B", "toStation": "C", "date": "2026-06-18", "price": 80.0, "stock": 0, "type": "ADULT"},
    {"id": 5, "fromStation": "A", "toStation": "C", "date": "2026-06-18", "price": 200.0, "stock": 100, "type": "ADULT"},
    {"id": 6, "fromStation": "A", "toStation": "B", "date": "2026-06-18", "price": 150.0, "stock": 1, "type": "STUDENT"},
]

tickets_db = [dict(t) for t in INITIAL_TICKETS]
db_lock = threading.Lock()

class OrderRequest(BaseModel):
    userId: Optional[int] = None
    ticketId: Optional[int] = None
    passengerType: Optional[str] = None

@app.post("/api/tickets/reset")
def reset_db():
    global tickets_db
    with db_lock:
        tickets_db = [dict(t) for t in INITIAL_TICKETS]
    return {"status": "success", "message": "Database reset"}

@app.get("/api/tickets/query")
def query_tickets(
    fromStation: Optional[str] = Query(None),
    toStation: Optional[str] = Query(None),
    date: Optional[str] = Query(None)
):
    if fromStation is None or toStation is None or date is None:
        raise HTTPException(status_code=400, detail="Missing required query parameters: fromStation, toStation, date")
    
    if not fromStation.strip() or not toStation.strip():
        raise HTTPException(status_code=400, detail="Station names cannot be empty")
    
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", date):
        raise HTTPException(status_code=400, detail="Invalid date format. Expected YYYY-MM-DD")
    
    try:
        datetime.strptime(date, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid calendar date")

    with db_lock:
        results = [
            t for t in tickets_db
            if t["fromStation"] == fromStation and t["toStation"] == toStation and t["date"] == date
        ]
    return results

@app.post("/api/tickets/order")
def order_ticket(order: OrderRequest):
    if order.userId is None or order.ticketId is None or order.passengerType is None:
        raise HTTPException(status_code=400, detail="Missing required fields in request body: userId, ticketId, passengerType")
    
    if not isinstance(order.userId, int) or order.userId <= 0:
        raise HTTPException(status_code=400, detail="userId must be a positive integer")
        
    if not isinstance(order.ticketId, int) or order.ticketId <= 0:
        raise HTTPException(status_code=400, detail="ticketId must be a positive integer")
        
    if not order.passengerType.strip():
        raise HTTPException(status_code=400, detail="passengerType cannot be empty")

    with db_lock:
        ticket = None
        for t in tickets_db:
            if t["id"] == order.ticketId:
                ticket = t
                break
        
        if ticket is None:
            raise HTTPException(status_code=400, detail="Ticket not found")
        
        if ticket["stock"] <= 0:
            raise HTTPException(status_code=400, detail="Ticket is out of stock")
        
        ticket["stock"] -= 1
        order_no = f"ORD{uuid.uuid4().hex[:8].upper()}"
        
    return {
        "success": True,
        "orderNo": order_no,
        "message": "Order is processing"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8080)
