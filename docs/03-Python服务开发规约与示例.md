# 03-Python服务开发规约与示例 (FastAPI)

本文档适用于 `python-service` 模块，重点约束 FastAPI 路由、算法服务拆分、模型输出校验、日志和依赖管理。

## 1. 模块职责

- 负责课件解析、向量检索、问答生成、学情诊断等智能能力。
- 作为内部服务被 Java 后端调用。
- 输出结构化结果，**不把不稳定的模型原始结果直接暴露出去**。

## 2. 推荐目录结构

```text
python-service/app/
├── main.py
├── api
│   └── v1
├── core
├── schemas
├── services
├── clients
├── repositories
├── utils
└── tests
```

## 3. 分层职责

### 3.1 api/v1

- 定义路由、请求模型、响应模型。
- 不写复杂业务和算法逻辑。

### 3.2 services

- 负责解析、切片、检索、Prompt 组装、答案生成、诊断等核心流程。
- 每个服务函数只负责清晰的一段业务动作。

### 3.3 clients

- 封装大模型、对象存储、Embedding、外部 SDK。
- 统一处理超时、重试和异常。

### 3.4 repositories

- 处理数据库、向量库等数据存取，不承担业务决策。

## 4. Schema 与模型输出规范

### 4.1 Pydantic Schema

- 输入输出模型分离。
- 字段必须带明确类型。
- 必要时声明默认值、校验规则、字段说明。

**代码示例：Pydantic 模型定义**

```python
# schemas/request.py
from pydantic import BaseModel, Field

class ParseRequest(BaseModel):
    courseware_id: str = Field(..., description="课件唯一标识")
    file_url: str = Field(..., description="课件的对象存储URL")
```

### 4.2 大模型输出校验（核心约束）

- 必须经过结构化解析和校验。
- 对空结果、缺字段、格式错乱做兜底处理。
- 不允许直接返回未经校验的自然语言大段文本作为结构数据。

**代码示例：强校验与兜底**

```python
# services/llm_service.py
def generate_script_outline(self, raw_text: str) -> Dict[str, Any]:
    prompt = "... 请严格按照以下JSON格式输出：..."
    response = self.model.invoke(prompt)
    
    try:
        clean_response = response.content.strip("`json\n").strip("`")
        data = json.loads(clean_response)
        # 强校验：确保字段齐全、类型正确
        validated_data = ParseResponse(**data)
        return validated_data.dict()
    except (json.JSONDecodeError, ValidationError) as e:
        logger.error(f"大模型输出格式异常: {e}")
        # 兜底策略：返回失败状态，绝不直接透传原始文本
        return {"pages": 0, "outline": [], "status": "FAILED"}
```

## 5. 服务实现规范

### 5.1 解析类流程推荐拆分

文件加载 -> 页面提取 -> 内容结构化 -> 图表与视觉信息抽取 -> 结果摘要输出。

### 5.2 RAG 类流程推荐拆分

内容切片 -> 向量化 -> 检索召回 -> Prompt 组装 -> 答案生成 -> 证据整理。

### 5.3 学情诊断流程

- 输入：学生提问、历史上下文、当前知识点。
- 输出：掌握度评分、风险判断、建议动作。
- 保证结果字段稳定，便于 Java 和前端消费。

## 6. 日志与异常

### 6.1 日志要求

- 统一使用 logging。
- 每次请求记录：入口接口、traceId、耗时、关键参数摘要。

**代码示例：中间件记录日志**

```python
# core/logging_config.py
async def log_requests(request: Request, call_next):
    trace_id = request.headers.get("X-Trace-Id", "N/A")
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time
    logger.info(f"TraceId: {trace_id} | Path: {request.url.path} | Duration: {process_time:.3f}s")
    return response
```

### 6.2 错误返回

- 使用标准 HTTP 状态码。
- 返回统一 JSON 结构。
- 错误信息避免暴露内部实现细节和密钥信息。

## 7. 不该做什么

- 不在一个函数里堆叠解析、检索、生成、落库全部逻辑。
- 不使用 `print` 代替正式日志。
- 不直接返回模型原始输出。
- 不在内部服务中承担账号权限和平台级鉴权逻辑。
