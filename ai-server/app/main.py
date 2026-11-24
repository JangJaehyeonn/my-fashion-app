from fastapi import FastAPI

from app.routers import clothes, diagnosis, embedding, purchase, recommend, vton, weather

app = FastAPI(title="Fashion AI Server", version="1.0.0")

app.include_router(clothes.router)
app.include_router(embedding.router)
app.include_router(purchase.router)
app.include_router(diagnosis.router)
app.include_router(recommend.router)
app.include_router(vton.router)
app.include_router(weather.router)


@app.get("/health")
async def health_check():
    return {"status": "ok"}
