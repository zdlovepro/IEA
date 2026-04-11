from fastapi import FastAPI
from app.services.vector_store import get_vector_store

app = FastAPI(title="AI Interactive Lecture - Python Service")

@app.on_event("startup")
async def startup_event():
    # Application startup: Initialize vector store connection
    try:
        # 建立数据库连接，首次启动可能下载权重模型（如果使用本地 embedding）
        vector_store = get_vector_store()
        print("Vector database is connected and ready.")
    except Exception as e:
        print(f"Error starting vector store dependency: {e}")

@app.get("/")
def read_root():
    return {"message": "Welcome to Python AI Service API"}