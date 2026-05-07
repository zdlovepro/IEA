# Interactive-Edu-Agent

Interactive-Edu-Agent 是一个多服务项目，提供课件解析、脚本生成、课堂播放以及问答集成等功能。默认启动路径为 `local`，仓库同时保留了 `full` 模式，支持 MySQL/Redis/MinIO 持久化存储。

## 项目结构

```text
backend/          Spring Boot 后端
python-service/   FastAPI 解析服务
frontend/         Vue 3 前端
docs/             规格说明与 API 文档
docker-compose-dev.yml
```

## 环境变量文件

### 根目录 `.env`

根目录下的 `.env` 用于：

- `docker-compose-dev.yml`
- 后端从 `backend/src/main/resources/application.yml` 加载的占位符值

创建方式：

```powershell
cd Interactive-Edu-Agent
cp .env.example .env
```

重要说明：

- `local` 模式可以直接使用示例值运行。
- `full` 模式需要你检查并填写 `.env` 中的 MySQL / Redis / MinIO 值。
- `SPRING_PROFILES_ACTIVE` 作为示例变量包含在内，但将后端切换到 `full` 最安全的方式仍然是下面提到的启动参数。

### Python 服务的 `.env`

Python 服务直接读取 `python-service/.env`。

```powershell
cd Interactive-Edu-Agent\python-service
cp .env.example .env
```

重要说明：

- 本地最小集成可将 `MILVUS_*` 和 `LLM_*` 值留空。
- 真正的 Milvus 检索需要 `MILVUS_URI` 及相关凭证。
- 真正的脚本生成或模型调用需要 `LLM_API_KEY` 以及对应的 `LLM_*` 配置。

### 前端的 `.env`

前端已经拥有安全的默认值在 `frontend/.env.example` 中。如果你想本地覆盖，复制为 `.env.local`：

```powershell
cd Interactive-Edu-Agent\frontend
cp .env.example .env.local
```

## 运行模式

### 本地最小集成模式（Local）

这是默认模式。

- Profile: `local`
- 不需要 MySQL、Redis 或 MinIO
- 存储路径: `backend/data/courseware`
- Python 解析服务: `http://localhost:8001/python/v1/parse`
- `TTS_ENABLED=false` 默认关闭

在此模式下最重要的变量：

- 根目录 `.env`：`PYTHON_CLIENT_BASE_URL`，可选的 `SPRING_PROFILES_ACTIVE`
- `python-service/.env`：默认值足够
- `frontend/.env.example`：默认值足够

### 完整持久化模式（Full）

该模式启用 Repository / JPA / MinIO 存储路径。

- Profile: `full`
- 需要 MySQL、Redis 和 MinIO
- 存储类型: `minio`
- `docker-compose-dev.yml` 读取根目录 `.env`
- `TTS_ENABLED=false` 默认关闭，因此除非你启用 TTS，否则阿里云密钥是可选的

完整模式所需的变量：

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `PYTHON_CLIENT_BASE_URL`

完整模式可选的变量：

- `REDIS_DATABASE`
- `REDIS_PASSWORD`
- `MINIO_SECURE`
- `TTS_ENABLED`
- `TTS_ALIYUN_APP_KEY`
- `TTS_ALIYUN_ACCESS_KEY_ID`
- `TTS_ALIYUN_ACCESS_KEY_SECRET`

## 文本转语音（TTS）

在 `local` 和 `full` 模式下 TTS 默认关闭。

- `TTS_ENABLED=false`：主解析 / 脚本生成 / 课堂流程保持纯文本，`audioUrl` 可能为 `null`
- `TTS_ENABLED=true`：后端将尝试合成脚本音频并为每个脚本节点附加 `audioUrl`
- 如果合成或上传失败，后端会降级为纯文本，不会导致课件解析或脚本生成失败

启用 TTS 的步骤：

1. 在根目录 `.env` 中设置 `TTS_ENABLED=true`
2. 填写 `TTS_ALIYUN_APP_KEY`
3. 填写 `TTS_ALIYUN_ACCESS_KEY_ID`
4. 填写 `TTS_ALIYUN_ACCESS_KEY_SECRET`

存储行为：

- `local` 模式返回后端提供的 URL，例如 `/api/v1/tts/audio/tts-audio/...`
- `full` 模式在 `storage.type=minio` 时返回 MinIO 签名的 URL

## 启动本地最小集成模式

1. 准备环境文件。

```powershell
cd Interactive-Edu-Agent
cp .env.example .env
cd python-service
cp .env.example .env
```

2. 启动 Python 服务。

```powershell
cd Interactive-Edu-Agent\python-service
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

3. 启动后端。

```powershell
cd Interactive-Edu-Agent\backend
mvn spring-boot:run
```

4. 启动前端。

```powershell
cd Interactive-Edu-Agent\frontend
npm install
npm run dev
```

访问地址：

- 前端：`http://localhost:5173`
- 后端健康检查：`http://localhost:8080/api/v1/health`
- Python 健康检查：`http://localhost:8001/python/v1/health`
- Python API 文档：`http://localhost:8001/docs`

## 启动完整持久化模式

1. 准备根目录 `.env` 并填写所需的 MySQL / Redis / MinIO 值。

```powershell
cd Interactive-Edu-Agent
cp .env.example .env
```

2. 启动 MySQL、Redis 和 MinIO。

```powershell
cd Interactive-Edu-Agent
docker compose -f docker-compose-dev.yml up -d
```

3. 准备并启动 Python 服务。

```powershell
cd Interactive-Edu-Agent\python-service
cp .env.example .env
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

4. 使用 `full` profile 启动后端。

```powershell
cd Interactive-Edu-Agent\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=full"
```

5. 启动前端。

```powershell
cd Interactive-Edu-Agent\frontend
npm install
npm run dev
```

## 集成验证清单

1. 从前端上传一个 `pptx` 或 `pdf` 文件。
2. 确认后端调用了 `POST /python/v1/parse`。
3. 等待课件状态进入可播放的路径。
4. 在课堂页面验证文本问答功能。

## 参考

- API 约定：`docs/05-接口与API通信规约.md`
- 前端环境示例：`frontend/.env.example`
- Python 环境示例：`python-service/.env.example`