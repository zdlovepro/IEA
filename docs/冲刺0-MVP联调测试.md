# 冲刺 0 MVP 联调测试

本文档用于帮助新人在本地完成 Sprint0 MVP 闭环联调，覆盖以下链路：

生成脚本 -> 语音播放 -> 语音打断 -> ASR -> 答疑流式输出 -> 断点续播

本文档只提供启动和测试方法，不包含任何真实密钥。

## 1. 组件与端口

- Python 服务：`http://localhost:8001`
- Java 后端：`http://localhost:8080`
- Vue 前端：`http://localhost:5173`
- 后端健康检查：`GET http://localhost:8080/api/v1/health`
- Python 健康检查：`GET http://localhost:8001/python/v1/health`
- 课堂 WebSocket：`ws://localhost:8080/ws/lecture?sessionId=<sessionId>`

建议联调顺序：

1. 先启动 Python 服务。
2. 再启动 Java 后端。
3. 最后启动 Vue 前端。

## 2. 启动前准备

### 2.1 建议的本地联调模式

为了保证 MVP 演示不断链，建议优先使用以下兜底模式：

- `SPRING_PROFILES_ACTIVE=local`
- `TTS_ENABLED=false`
- `ASR_ENABLED=false`
- `ASR_PROVIDER=local`
- 不配置 Milvus，使用 fallback 检索
- 不配置 LLM Key 时，Python 侧会返回模板讲稿和模板问答

如果要演示完整云能力，再逐步开启：

- TTS：打开 `TTS_ENABLED=true`
- ASR：打开 `ASR_ENABLED=true`，并配置 DashScope Key
- Embedding：配置 DashScope embedding
- LLM：配置 DeepSeek Key

### 2.2 `.env` 示例说明

下面是联调时常用的配置说明。请注意：

- 以下内容只展示空模板，不要填写真实 key 到文档里。
- 团队口径中的变量名，和当前项目实际读取的变量名，部分并不完全一致。
- 如果两者不一致，以“项目实际读取变量名”为准。

#### DeepSeek

团队口径：

```env
LLM_API_KEY=
LLM_API_BASE=https://api.deepseek.com
LLM_MODEL_NAME=deepseek-v4-pro
```

项目实际读取：

- Python 服务读取 `LLM_API_KEY`
- 兼容别名 `DEEPSEEK_API_KEY`
- 默认 `LLM_API_BASE=https://api.deepseek.com`
- 默认 `LLM_MODEL_NAME=deepseek-v4-pro`

#### 阿里云 TTS

团队口径：

```env
DASHSCOPE_API_KEY=
TTS_PROVIDER=COSYVOICE
TTS_ENABLED=true
```

项目实际读取：

```env
TTS_ENABLED=true
DASHSCOPE_API_KEY=
TTS_DASHSCOPE_MODEL=cosyvoice-v3-flash
TTS_DASHSCOPE_VOICE=longanyang
TTS_DASHSCOPE_WEBSOCKET_URL=wss://dashscope.aliyuncs.com/api-ws/v1/inference
TTS_DASHSCOPE_FORMAT=mp3
TTS_DASHSCOPE_SAMPLE_RATE=24000
TTS_DASHSCOPE_SPEECH_RATE=0
TTS_DASHSCOPE_PITCH_RATE=0
TTS_DASHSCOPE_VOLUME=50
```

说明：

- 当前代码实际不依赖 `TTS_PROVIDER=COSYVOICE` 这个变量。
- 是否开启，主要由 `TTS_ENABLED` 控制。

#### 阿里云 ASR

团队口径：

```env
ASR_ENABLED=false
ASR_PROVIDER=local
ALIYUN_ASR_API_KEY=
```

项目实际读取：

```env
ASR_ENABLED=false
ASR_PROVIDER=local
ASR_QWEN_API_KEY=
QWEN_ASR_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_ASR_MODEL=qwen3-asr-flash
QWEN_ASR_ENABLE_ITN=true
QWEN_ASR_LANGUAGE=
QWEN_ASR_MAX_FILE_SIZE_MB=10
QWEN_ASR_TIMEOUT_SECONDS=60
```

说明：

- 当前后端实际读取的是 `ASR_QWEN_API_KEY`。
- `ALIYUN_ASR_API_KEY` 可以作为团队沟通口径保留，但不会被当前代码直接消费。
- 本地演示建议直接使用：

```env
ASR_ENABLED=false
ASR_PROVIDER=local
```

#### 阿里云 Embedding

团队口径与项目实际读取一致：

```env
EMBEDDING_PROVIDER=dashscope
EMBEDDING_API_KEY=
EMBEDDING_API_BASE=https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_MODEL_NAME=text-embedding-v4
EMBEDDING_DIM_SIZE=1024
```

说明：

- 如果 `EMBEDDING_API_KEY` 为空，Python 会尝试回退到 `DASHSCOPE_API_KEY`。
- 如果 DashScope Embedding 不可用，会继续回退到 keyword fallback repository。

#### 兜底说明

Spring 配置口径：

```env
TTS_ENABLED=false
ASR_ENABLED=false
```

运行时行为：

- `tts.enabled=false` 等价于环境变量 `TTS_ENABLED=false`
- `asr.enabled=false` 等价于环境变量 `ASR_ENABLED=false`
- 无 Milvus 时，Python 服务仍可启动，并自动回退到内存 / 关键词检索

## 3. 启动步骤

### 3.1 启动 Python 服务

在 `D:\205zd\Desktop\IEA\python-service` 下执行：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

预期结果：

- 控制台显示 `Uvicorn running on http://0.0.0.0:8001`
- 访问 [http://localhost:8001/python/v1/health](http://localhost:8001/python/v1/health) 返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ok"
  }
}
```

### 3.2 启动 Java 后端

在 `D:\205zd\Desktop\IEA\backend` 下执行：

```powershell
mvn spring-boot:run
```

预期结果：

- 控制台显示 Spring Boot 启动完成
- 访问 [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health) 返回 `code=0`

### 3.3 启动 Vue 前端

在 `D:\205zd\Desktop\IEA\frontend` 下执行：

```powershell
npm install
npm run dev
```

预期结果：

- 控制台显示 Vite 启动成功
- 浏览器访问 [http://localhost:5173](http://localhost:5173)
- 首页正常显示，不白屏

## 4. MVP 联调流程

### 4.1 上传课件

操作：

1. 打开前端首页。
2. 进入“上传课件”页面。
3. 上传 `pdf / ppt / pptx` 课件。

预期结果：

- 上传成功后显示课件条目
- 课件状态进入 `UPLOADED`，随后进入 `PARSING`

定位建议：

- 前端上传失败：先看浏览器 Network 的 `/api/v1/courseware/upload`
- Java 报错：看后端日志
- Python 解析失败：看 Python `/python/v1/parse` 对应日志

### 4.2 等待解析完成

操作：

1. 轮询课程详情或课程列表。
2. 等待状态变为 `PARSED` 或 `READY`。

预期结果：

- `PARSED`：说明课件解析已完成，可生成讲稿
- `READY`：说明脚本和音频已经准备好

### 4.3 生成讲稿

操作：

1. 进入课程详情或讲稿页。
2. 点击“生成讲稿”。

预期结果：

- 接口立即返回成功
- 课件状态从 `PARSED` 变为 `GENERATING_SCRIPT`
- 稍后状态变为 `READY` 或 `FAILED`

定位建议：

- Python 正常时，会优先调用 `/python/v1/script/generate`
- Python 不可用时，Java 会回退到本地 fallback 讲稿

### 4.4 进入课堂

操作：

1. 打开“进入课堂”。
2. 等待前端自动调用开始课堂接口。

预期结果：

- 返回 `sessionId`
- 课堂状态从 `IDLE` 进入 `PLAYING`
- 页面连接 WebSocket 成功时，会显示已连接状态

### 4.5 播放第一页音频

操作：

1. 点击播放。
2. 观察播放器模式。

预期结果：

- 有 `audioUrl` 时，播放器模式显示“音频播放”
- 无 `audioUrl` 时，自动降级到“文本朗读”
- 不会出现白屏或控制区消失

### 4.6 开启语音打断

操作：

1. 点击“开启语音打断”。
2. 允许浏览器麦克风权限。

预期结果：

- 页面显示麦克风已开启
- 能看到倾听状态或音量变化提示

### 4.7 说一句问题

操作：

1. 在课堂播放中直接说一句简短问题，例如：
   `这个概念和上一页有什么关系？`

预期结果：

- VAD 检测到说话
- 页面暂停讲解
- 前端发送 WebSocket `interrupt`
- 后端保存 `pageIndex` 和 `currentTime`

### 4.8 ASR 返回文字

操作：

1. 说完问题后保持静音约 2 秒。

预期结果：

- 前端停止录音
- 调用 `/api/v1/asr/recognize`
- 如果 ASR 正常，问题文本会自动填入问答区
- 如果 ASR 失败，前端提示“识别失败，请手动输入问题。”

### 4.9 SSE 流式答疑

操作：

1. ASR 识别成功后，前端会自动发起问答。

预期结果：

- 优先调用 `/api/v1/qa/stream`
- 回答逐段显示
- Markdown 渲染正常
- SSE 失败时，自动降级到 `POST /api/v1/qa/ask-text`

### 4.10 答疑结束后断点续播

操作：

1. 等待流式回答完成。
2. 或手动点击“继续课堂”。

预期结果：

- 前端发送 WebSocket `resume`
- 同时调用后端 `resume` 接口
- 如果当前页有音频，则从断点时间附近继续播放
- 如果当前页没有音频，则恢复到 `PLAYING` 状态并继续文本朗读流程

### 4.11 查看打断和问答记录

操作：

1. 调用记录接口，或在后端本地查看 jsonl 文件。

预期结果：

- 能查到 interrupt 记录
- 能查到 qa 记录
- 本地文件路径一般为：
  `backend/data/records/<sessionId>.jsonl`

## 5. curl 示例

以下命令默认后端地址为 `http://localhost:8080`。

说明：

- 以下示例按 bash / Git Bash / WSL 语法编写。
- 如果你在 Windows PowerShell 中执行，建议把 `curl` 替换为 `curl.exe`。
- `scripts/sprint0-smoke-test.sh` 也建议在 Git Bash 或 WSL 中运行。

### 5.1 上传课件

```bash
curl -X POST "http://localhost:8080/api/v1/courseware/upload" \
  -F "file=@./demo-courseware.pptx" \
  -F "name=冲刺0联调示例课件"
```

预期结果：

- 返回 `code=0`
- `data` 中可拿到 `coursewareId`

### 5.2 获取课件详情

```bash
curl "http://localhost:8080/api/v1/courseware/<coursewareId>"
```

预期结果：

- 返回当前状态，如 `PARSING / PARSED / READY / FAILED`

### 5.3 生成讲稿

```bash
curl -X POST "http://localhost:8080/api/v1/courseware/<coursewareId>/script/generate"
```

预期结果：

- 立即返回成功
- 后台异步生成讲稿

### 5.4 获取讲稿

```bash
curl "http://localhost:8080/api/v1/courseware/<coursewareId>/script"
```

预期结果：

- 若已生成完成，返回 `opening / pages / closing`
- `pages[*].audioUrl` 可能为有效 URL，也可能为 `null`

### 5.5 开始课堂

```bash
curl -X POST "http://localhost:8080/api/v1/lecture/start" \
  -H "Content-Type: application/json" \
  -d "{\"coursewareId\":\"<coursewareId>\",\"userId\":\"demo-user\"}"
```

预期结果：

- 返回 `sessionId`
- 返回初始 `status`

### 5.6 暂停课堂

```bash
curl -X POST "http://localhost:8080/api/v1/lecture/<sessionId>/pause"
```

预期结果：

- 返回 `code=0`
- 课堂状态更新

### 5.7 恢复课堂

```bash
curl -X POST "http://localhost:8080/api/v1/lecture/resume" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"<sessionId>\"}"
```

预期结果：

- 返回 `breakpointTime`
- 返回 `pageIndex`
- 前端可据此恢复播放

### 5.8 文本问答

```bash
curl -X POST "http://localhost:8080/api/v1/qa/ask-text" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"<sessionId>\",\"question\":\"这页内容的重点是什么？\"}"
```

预期结果：

- 返回 `answer`
- 返回 `evidence`

### 5.9 ASR 识别

```bash
curl -X POST "http://localhost:8080/api/v1/asr/recognize" \
  -F "file=@./question.webm" \
  -F "sessionId=<sessionId>" \
  -F "pageIndex=1"
```

预期结果：

- 返回 `text`
- 返回 `confidence`
- 返回 `durationMs`

### 5.10 SSE 流式问答

```bash
curl -N "http://localhost:8080/api/v1/qa/stream?sessionId=<sessionId>&question=%E8%BF%99%E4%B8%80%E9%A1%B5%E7%9A%84%E9%87%8D%E7%82%B9%E6%98%AF%E4%BB%80%E4%B9%88%EF%BC%9F&topK=5"
```

预期结果：

- 能持续看到 `data: {"type":"delta"...}`
- 最终出现 `data: {"type":"done"}`

### 5.11 获取课堂记录

```bash
curl "http://localhost:8080/api/v1/lecture/<sessionId>/records"
```

预期结果：

- 返回 `interrupts`
- 返回 `qaRecords`

## 6. 常见问题

### 6.1 Python 连接失败

现象：

- Java 调用 Python 超时
- 讲稿生成、问答、解析变慢或走 fallback

排查：

1. 先访问 [http://localhost:8001/python/v1/health](http://localhost:8001/python/v1/health)
2. 再检查根目录 `.env` 或环境变量中的 `PYTHON_CLIENT_BASE_URL`
3. 查看 Java 后端日志中 Python client 的 timeout / fallback 记录

结论定位：

- Python 服务问题，或 Java 到 Python 的网络配置问题

### 6.2 DeepSeek Key 缺失

现象：

- Python script / qa 仍能返回内容，但质量偏模板化

排查：

1. 检查 `LLM_API_KEY`
2. 检查 `LLM_API_BASE`
3. 检查 `LLM_MODEL_NAME`

结论定位：

- 第三方 LLM 配置缺失
- 不会阻断 MVP，只会走模板兜底

### 6.3 DashScope Embedding Key 缺失

现象：

- 向量检索效果变差
- 仍能问答，但 evidence 更可能来自 fallback 检索

排查：

1. 检查 `EMBEDDING_API_KEY`
2. 检查 `DASHSCOPE_API_KEY`
3. 查看 Python 日志是否提示 fallback 到 keyword repository

结论定位：

- 第三方 Embedding 配置缺失

### 6.4 TTS Key 缺失

现象：

- `audioUrl` 为 `null`
- 课堂页仍然可用，但会回退到文本朗读

排查：

1. 检查 `TTS_ENABLED`
2. 检查 `DASHSCOPE_API_KEY`
3. 检查 Java 日志中 TTS 是否已降级

结论定位：

- 第三方 TTS 配置问题
- 不阻断课堂演示

### 6.5 麦克风无权限

现象：

- 无法开启语音打断
- 前端提示无法访问麦克风

排查：

1. 检查浏览器站点权限
2. 检查是否使用了 `http://localhost`
3. 改为手动输入问题继续演示

结论定位：

- 前端浏览器权限问题

### 6.6 SSE 不返回

现象：

- 流式回答没有逐段输出

排查：

1. 先看浏览器 Network 里的 `/api/v1/qa/stream`
2. 再看 Java 是否已代理到 Python
3. 再看 Python `/python/v1/qa/stream` 是否持续输出 `data:` 事件

结论定位：

- Java SSE 代理问题，或 Python 流式问答问题
- 前端应自动降级到 `POST /api/v1/qa/ask-text`

### 6.7 WebSocket 连接失败

现象：

- 课堂页不能实时发送 `interrupt / resume`

排查：

1. 检查浏览器控制台中的 `/ws/lecture`
2. 检查后端是否已启动 WebSocket endpoint
3. 检查前端开发代理是否转发 `/ws`

结论定位：

- Java WebSocket 或前端代理问题
- 不应影响手动问答和正常播放

### 6.8 Milvus 不可用时如何走 fallback

现象：

- Python 服务仍能启动
- 问答仍可继续，但检索效果会弱一些

排查：

1. 查看 Python 启动日志是否提示 fallback vector store
2. 查看 `MILVUS_URI` 是否为空或不可达

结论定位：

- 向量库问题
- 不阻断 MVP

### 6.9 记录文件写入失败如何排查

现象：

- `/api/v1/lecture/<sessionId>/records` 仍可能正常返回内存记录
- 但本地 `jsonl` 文件缺失

排查：

1. 检查 `backend/data/records` 目录是否可写
2. 检查 Windows 权限或占用
3. 查看 Java 日志中的 record write failure

结论定位：

- 本地文件写入权限问题
- 不应阻断主流程

### 6.10 前端页面空白如何排查

现象：

- 浏览器打开后白屏

排查：

1. 看浏览器 Console 是否有编译错误
2. 看 `npm run dev` 是否正常启动
3. 检查 `VITE_API_BASE_URL`
4. 检查接口 500 是否导致页面初始化失败

结论定位：

- 前端构建或运行时错误
- 先排前端，再排接口

## 7. 故障定位速查

- 上传失败：优先看前端和 Java `/courseware/upload`
- 解析失败：优先看 Python `/python/v1/parse`
- 讲稿生成失败：优先看 Python `/python/v1/script/generate`，再看 Java fallback
- 音频播放失败：优先看 TTS 和 `audioUrl`
- 语音打断失败：优先看浏览器麦克风权限和 WebSocket
- ASR 失败：优先看 `/api/v1/asr/recognize`
- 流式问答失败：优先看 `/api/v1/qa/stream` 和 `/python/v1/qa/stream`
- 断点续播失败：优先看 Java `resume` 返回和前端播放器 seek

## 8. 联调完成标准

满足以下条件即可认为 Sprint0 MVP 联调通过：

1. 能上传一个课件。
2. 课件能完成解析。
3. 能生成讲稿。
4. 能进入课堂。
5. 第一页可以播放音频，或降级文本朗读。
6. 能语音打断。
7. ASR 能识别问题，或可手动输入继续。
8. SSE 能流式输出回答，或自动回退同步问答。
9. 答疑结束后可以继续播放。
10. 能查到 interrupt 和 qa 记录。

## 9. 说明

- 本文档不要求你在第一次联调时把所有第三方能力都配置好。
- 建议先跑通 fallback MVP，再逐步打开 TTS、ASR、Embedding、DeepSeek。
- 如需快速演示，优先保证“上传 -> 讲稿 -> 课堂 -> 手动问答 -> 记录查询”这条主链可用。
