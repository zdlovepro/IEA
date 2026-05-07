# Interactive-Edu-Agent API 文档

本文档用于统一项目对外接口、内部服务调用接口、状态字段和错误码约定，便于前后端联调、跨服务协作和后续平台集成。

---

## 1. 文档说明

### 1.1 适用范围
- 面向 `frontend` 调用 `backend` 的对外接口
- 面向 `backend` 调用 `python-service` 的内部接口
- 面向后续与泛雅平台对接的标准化 API 说明

### 1.2 接口设计原则
- 统一版本前缀
- 统一响应结构
- 统一错误码语义
- 状态字段可枚举、可追踪
- 支持链路追踪与问题排查

---

## 2. 通用约定

### 2.1 基础路径
- 对外网关接口：`/api/v1`
- Python 内部服务接口：`/python/v1`

### 2.2 请求格式
- 普通请求：`application/json`
- 文件上传：`multipart/form-data`
- 字符编码：`UTF-8`

### 2.3 响应格式

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": 40001,
  "message": "参数错误：coursewareId 不能为空",
  "data": null
}
```

分页响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "total": 0,
    "page": 1,
    "pageSize": 20
  }
}
```

### 2.4 公共请求头
- `Authorization: Bearer <token>`：需要登录态时使用
- `X-Trace-Id: <traceId>`：推荐透传链路 ID
- `Content-Type`：按接口类型设置

### 2.5 通用错误码
- `0`：成功
- `40001`：参数缺失或格式错误
- `40002`：业务校验失败
- `40101`：未登录或 Token 无效
- `40301`：无权限访问
- `40401`：资源不存在
- `40901`：状态冲突或重复操作
- `50001`：服务内部错误
- `50201`：下游 Python 服务异常
- `50202`：第三方模型或媒体服务异常

---

## 3. 核心业务对象

### 3.1 课件对象

```json
{
  "coursewareId": "cware_123456",
  "name": "数据结构导论",
  "fileUrl": "https://example.com/files/xxx.pptx",
  "status": "READY",
  "createdAt": "2026-04-16T10:00:00Z",
  "updatedAt": "2026-04-16T10:10:00Z"
}
```

### 3.2 解析任务对象

```json
{
  "taskId": "task_parse_001",
  "coursewareId": "cware_123456",
  "status": "RUNNING",
  "progress": 45,
  "message": "正在提取页面与结构化内容"
}
```

### 3.3 讲课会话对象

```json
{
  "sessionId": "sess_789",
  "coursewareId": "cware_123456",
  "status": "PLAYING",
  "currentNodeId": "node_001",
  "currentPage": 3,
  "resumeToken": "node_001|offset_123"
}
```

### 3.4 问答结果对象

```json
{
  "answer": "递归是函数直接或间接调用自身的一种方法。",
  "evidence": [
    {
      "source": "page_3",
      "text": "递归通常需要终止条件。"
    }
  ],
  "latencyMs": 1250
}
```

---

## 4. 状态枚举

### 4.1 课件状态
- `UPLOADED`：已上传
- `PARSING`：解析中
- `PARSED`：解析完成
- `GENERATING_SCRIPT`：讲稿生成中
- `READY`：可开始讲课
- `FAILED`：处理失败

### 4.2 讲课会话状态
- `IDLE`：未开始
- `PLAYING`：讲解中
- `INTERRUPTED`：被打断
- `ANSWERING`：问答中
- `RESUMING`：恢复讲课中
- `ENDED`：已结束

### 4.3 解析任务状态
- `PENDING`：待执行
- `RUNNING`：执行中
- `SUCCESS`：成功
- `FAILED`：失败

---

## 5. 对外 API

### 5.1 上传课件
- 方法：`POST`
- 路径：`/api/v1/courseware/upload`
- 描述：上传 PPT/PDF 等课件文件，创建课件记录

请求参数：
- `file`：必填，课件文件
- `name`：选填，课件名称

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "coursewareId": "cware_123456",
    "status": "UPLOADED"
  }
}
```

### 5.2 创建解析任务
- 方法：`POST`
- 路径：`/api/v1/courseware/parse`
- 描述：为指定课件创建解析任务

请求示例：

```json
{
  "coursewareId": "cware_123456"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "task_parse_001",
    "status": "PENDING"
  }
}
```

### 5.3 查询课件详情
- 方法：`GET`
- 路径：`/api/v1/courseware/{coursewareId}`
- 描述：获取课件状态、基础信息和处理进度

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "coursewareId": "cware_123456",
    "name": "数据结构导论",
    "status": "READY",
    "currentTaskStatus": "SUCCESS"
  }
}
```

### 5.4 查询课件列表
- 方法：`GET`
- 路径：`/api/v1/courseware`
- 描述：分页查询课件列表

请求参数：
- `page`：页码
- `pageSize`：每页数量
- `status`：可选，按状态过滤

### 5.5 开始讲课
- 方法：`POST`
- 路径：`/api/v1/lecture/start`
- 描述：创建讲课会话并返回首个讲解节点

请求示例：

```json
{
  "coursewareId": "cware_123456",
  "userId": "user_001"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "sess_789",
    "status": "PLAYING",
    "currentNode": {
      "nodeId": "node_001",
      "pageIndex": 1,
      "content": "本页主要讲解递归的基本定义。",
      "audioUrl": "https://example.com/audio/001.mp3"
    }
  }
}
```

### 5.6 暂停讲课
- 方法：`POST`
- 路径：`/api/v1/lecture/pause`
- 描述：主动暂停当前讲课会话

请求示例：

```json
{
  "sessionId": "sess_789"
}
```

### 5.7 恢复讲课
- 方法：`POST`
- 路径：`/api/v1/lecture/resume`
- 描述：从最近断点恢复讲课

请求示例：

```json
{
  "sessionId": "sess_789",
  "resumeToken": "node_001|offset_123"
}
```

### 5.8 结束讲课
- 方法：`POST`
- 路径：`/api/v1/lecture/end`
- 描述：结束当前讲课会话

### 5.9 文本问答
- 方法：`POST`
- 路径：`/api/v1/qa/ask-text`
- 描述：基于当前讲课上下文发起文本问答

请求示例：

```json
{
  "sessionId": "sess_789",
  "question": "什么是递归？"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "递归是函数直接或间接调用自身的一种方法。",
    "evidence": [
      {
        "source": "page_3",
        "text": "递归通常需要终止条件。"
      }
    ],
    "latencyMs": 1250
  }
}
```

### 5.10 语音问答
- 方法：`POST`
- 路径：`/api/v1/qa/ask-voice`
- 描述：提交语音流或语音文件，服务端识别后进入问答流程

请求参数：
- `sessionId`
- `audio`

### 5.11 查询会话详情
- 方法：`GET`
- 路径：`/api/v1/session/{sessionId}`
- 描述：查询讲课会话当前状态、当前节点、恢复信息

### 5.12 查询问答记录
- 方法：`GET`
- 路径：`/api/v1/session/{sessionId}/qa-records`
- 描述：分页获取问答历史

### 5.13 学情诊断结果
- 方法：`GET`
- 路径：`/api/v1/session/{sessionId}/learning-diagnosis`
- 描述：获取当前会话的学情判断结果、掌握度评分和建议动作

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "score": 62,
    "level": "MEDIUM",
    "suggestion": "建议回讲当前知识点并插入一次随堂提问"
  }
}
```

---

## 6. Python 内部服务 API

### 6.1 解析课件
- 方法：`POST`
- 路径：`/python/v1/parse`
- 描述：解析上传后的课件内容，返回结构化结果摘要

请求示例：

```json
{
  "coursewareId": "cware_123456",
  "fileUrl": "https://example.com/files/xxx.pptx"
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "pages": 24,
    "outline": [
      "递归定义",
      "递归终止条件",
      "递归与栈"
    ]
  }
}
```

### 6.2 生成讲稿
- 方法：`POST`
- 路径：`/python/v1/script/generate`
- 描述：根据课件各页解析结果，调用大模型生成包含开场白、各页讲解、过渡语和结语的完整口语化讲稿。由 Java 后端在课件状态变为 `PARSED` 后调用，结果用于驱动后续 TTS 合成流程。

请求示例：

```json
{
  "courseware_id": "cware_123456",
  "courseware_name": "数据结构导论",
  "subject": "计算机科学",
  "pages": [
    {
      "page_index": 1,
      "title": "递归简介",
      "text_content": "递归是函数直接或间接调用自身的编程技术，需要有终止条件。",
      "keywords": ["递归", "终止条件", "函数调用"]
    },
    {
      "page_index": 2,
      "title": "斐波那契数列",
      "text_content": "斐波那契数列 F(n)=F(n-1)+F(n-2)，是递归的典型应用场景。",
      "keywords": ["斐波那契", "递归树", "时间复杂度"]
    }
  ]
}
```

响应示例（成功）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "courseware_id": "cware_123456",
    "opening": "大家好，欢迎来到《数据结构导论》的学习！本节我们将重点探讨递归这一核心编程思想……",
    "pages": [
      {
        "page_index": 1,
        "script": "首先我们来看递归的基本概念。递归，简单来说就是一个函数在执行过程中调用自身……",
        "transition": "了解了递归的定义之后，接下来我们看一个经典的递归应用案例——斐波那契数列。"
      },
      {
        "page_index": 2,
        "script": "斐波那契数列是递归最直观的体现之一。F(n)=F(n-1)+F(n-2)，每一项都依赖前两项……",
        "transition": "以上就是斐波那契数列的核心要点，大家可以在课后尝试手写实现。"
      }
    ],
    "closing": "好，本节课我们掌握了递归的定义、终止条件以及斐波那契这个经典案例，希望大家课后多加练习！"
  }
}
```

响应示例（失败）：

```json
{
  "code": 50001,
  "message": "大模型服务异常：ValueError",
  "data": null
}
```

字段说明：
- `pages[].script`：该页口语化讲解内容，约 150-300 字
- `pages[].transition`：过渡到下一页的衔接语；最后一页为本页小结语，约 30-50 字
- `opening`：整节课开场白，约 100-150 字
- `closing`：整节课结语，约 50-80 字

### 6.3 构建向量索引
- 方法：`POST`
- 路径：`/python/v1/vectorize`
- 描述：将切片内容写入向量库

### 6.4 检索问答上下文
- 方法：`POST`
- 路径：`/python/v1/rag/retrieve`
- 描述：按问题和当前上下文召回证据片段

### 6.5 生成问答结果
- 方法：`POST`
- 路径：`/python/v1/qa/answer`
- 描述：结合检索结果生成答案、证据和掌握度判断

### 6.6 学情诊断
- 方法：`POST`
- 路径：`/python/v1/diagnosis/analyze`
- 描述：分析学生提问与历史行为，输出掌握度和建议动作

---

## 7. TTS 语音合成内部接口

> **适用范围**：`backend` Java 服务内部调用，不对外暴露。由 Service 层注入 `TtsClient` 使用（如任务7批量预合成、单段实时播报）。

### 7.1 TTS 合成请求对象（TtsRequest）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `text` | `String` | ✅ | 待合成文本，最多 1000 个字符 |
| `voice` | `String` | ❌ | 发音人（如 `aixia`/`aiyu`/`aijia`），默认使用配置值 |
| `format` | `String` | ❌ | 音频格式：`wav`/`mp3`/`pcm`，默认 `wav` |
| `sampleRate` | `Integer` | ❌ | 采样率（Hz），支持 `8000`/`16000`，默认 `16000` |
| `speechRate` | `Integer` | ❌ | 语速，-500~500，0 为正常，默认 `0` |
| `pitchRate` | `Integer` | ❌ | 语调，-500~500，0 为正常，默认 `0` |
| `volume` | `Integer` | ❌ | 音量，0~100，默认 `50` |

### 7.2 TTS 合成结果对象（TtsResult）

| 字段 | 类型 | 说明 |
|---|---|---|
| `audioData` | `byte[]` | 合成后的音频二进制数据 |
| `format` | `String` | 实际音频格式（与请求中 `format` 一致） |
| `sampleRate` | `int` | 实际采样率（Hz） |
| `requestId` | `String` | 服务端请求 ID（来自 `X-NLS-RequestId` 响应头），用于问题排查 |

### 7.3 接口方法

```java
// 同步合成（阻塞当前线程，适用于单段实时播报）
TtsResult synthesize(TtsRequest request);

// 异步合成（在 ttsTaskExecutor 线程池执行，适用于批量预生成）
CompletableFuture<TtsResult> synthesizeAsync(TtsRequest request);
```

### 7.4 鉴权流程

1. 使用 `AccessKeyId` + `AccessKeySecret` 向 NLS Token 端点换取短效 Token（24 小时有效）
2. Token 在内存中缓存，提前 60 秒自动刷新
3. 每次合成请求将 Token 与 AppKey 作为 Query 参数附加（`?appkey=X&token=Y`）
4. **含 Token 的完整 URL 禁止打印到日志**（安全规约 §10）

### 7.5 配置项（`application.yml` 前缀 `tts.aliyun`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `app-key` | — | 阿里云 NLS 项目 AppKey（必填） |
| `access-key-id` | — | RAM AccessKeyId（必填） |
| `access-key-secret` | — | RAM AccessKeySecret（必填） |
| `endpoint` | `https://nls-gateway.cn-shanghai.aliyuncs.com` | NLS 网关地址 |
| `voice` | `aixia` | 默认发音人 |
| `format` | `wav` | 默认音频格式 |
| `sample-rate` | `16000` | 默认采样率（Hz） |
| `speech-rate` | `0` | 默认语速 |
| `pitch-rate` | `0` | 默认语调 |
| `volume` | `50` | 默认音量 |
| `connect-timeout-ms` | `5000` | HTTP 连接超时（毫秒） |
| `read-timeout-ms` | `30000` | HTTP 读取超时（毫秒） |

### 7.6 错误码

| 错误码 | 枚举 | 说明 |
|---|---|---|
| `40011` | `TTS_INVALID_REQUEST` | 请求参数非法（文本为空或超过 1000 字符） |
| `50211` | `TTS_TOKEN_FETCH_FAILED` | Token 获取失败（凭证错误或网络异常） |
| `50212` | `TTS_SYNTHESIS_FAILED` | TTS 合成失败（上游服务 4xx/5xx） |
| `50213` | `TTS_AUDIO_UPLOAD_FAILED` | 音频上传至对象存储失败（MinIO 不可用或音频数据为空） |

---

### 7.7 TTS 音频对象存储服务（TtsAudioStorageService）

> 负责将 TTS 合成产生的音频字节流上传至 MinIO，并生成带有效期的预签名 GET 直链，供客户端直接播放，无需经过后端中转流量。

#### 接口方法

```java
// 上传音频并生成预签名直链
String uploadAndSign(String objectKey, byte[] audioData, String format, Integer expiryMins);

// 生成规范化的对象 Key
String generateObjectKey(String format);
```

#### 对象 Key 格式

```
tts-audio/{yyyy}/{MM}/{uuid}.{format}
```

示例：`tts-audio/2026/04/550e8400-e29b-41d4-a716-446655440000.wav`

#### 预签名直链示例

```
http://localhost:9000/interactive-edu/tts-audio/2026/04/xxx.wav?X-Amz-Algorithm=...&X-Amz-Expires=3600&...
```

- 直链为 HTTP GET，客户端可直接播放
- 有效期由 `tts.presigned-expiry-minutes`（默认 60 分钟）控制
- 链接过期后需重新调用接口获取新链接

#### 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `tts.presigned-expiry-minutes` | `60` | 预签名直链有效期（分钟） |

#### Content-Type 映射

| 格式 | Content-Type |
|---|---|
| `wav` | `audio/wav` |
| `mp3` | `audio/mpeg` |
| `pcm` | `audio/L16` |

#### 典型调用链

```java
TtsResult result = ttsClient.synthesize(TtsRequest.builder().text("本页内容").build());
String key  = ttsAudioStorageService.generateObjectKey(result.getFormat());
String url  = ttsAudioStorageService.uploadAndSign(key, result.getAudioData(), result.getFormat(), null);
// url 即为可播放的预签名直链，有效期 60 分钟
```

---

## 8. 接口设计约定

> 前端不得自行推断本文档化的字段含义

### 8.1 命名约定

- 路径
- 方法
- 请求格式
- 状态字段
- 错误码

### 8.2 后端与 Python 服务联调
- 统一超时、重试和日志追踪策略
- 对模型类返回值必须做结构化校验
- 内部接口即使不直接暴露给前端，也必须保持响应结构统一

---

## 9. 文档维护要求

- 新增接口时同步补充本文件
- 调整字段、状态值、错误码时同步修改示例
- 关键流程发生变化时同步更新"典型业务流程"章节
- 如果实际代码中接口路由与本文档不一致，以修正文档和代码其中之一的方式尽快收敛，不允许长期异步
| `text` | String | 是 | 待合成文本，最多 1000 字符 |
| `voice` | String | 否 | 发音人，如 `aixia`（女）、`aiyu`（男）；为 null 时使用配置默认值 |
| `sampleRate` | Integer | 否 | 采样率（Hz），支持 `8000` / `16000`；为 null 时使用配置默认值 |
| `format` | String | 否 | 音频格式：`wav` / `mp3` / `pcm`；为 null 时使用配置默认值 |
| `speechRate` | Integer | 否 | 语速，范围 `-500~500`，`0` 为正常；为 null 时使用配置默认值 |
| `pitchRate` | Integer | 否 | 语调，范围 `-500~500`，`0` 为正常；为 null 时使用配置默认值 |
| `volume` | Integer | 否 | 音量，范围 `0~100`；为 null 时使用配置默认值 |

### 7.2 TTS 合成结果对象（TtsResult）

| 字段 | 类型 | 说明 |
|---|---|---|
| `audioData` | byte[] | 合成后的音频二进制数据 |
| `format` | String | 实际音频格式（`wav` / `mp3` / `pcm`） |
| `sampleRate` | int | 实际采样率（Hz） |
| `requestId` | String | 服务端请求 ID（来自 `X-NLS-RequestId` 响应头，用于问题排查） |

### 7.3 TtsClient 接口方法

#### 同步合成

```java
TtsResult synthesize(TtsRequest request)
```

- 调用方线程阻塞等待，直到音频数据完整返回
- 适用场景：单段脚本实时播报（对延迟敏感）
- 抛出：`TtsException`（含 `ErrorCode.TTS_SYNTHESIS_FAILED` 或 `TTS_TOKEN_FETCH_FAILED`）

#### 异步合成

```java
CompletableFuture<TtsResult> synthesizeAsync(TtsRequest request)
```

- 立即返回，合成任务在专用线程池 `ttsTaskExecutor`（4核/8最大/200队列）中执行
- 适用场景：任务7批量预生成全页音频（不阻塞主流程）
- 异常通过 `CompletableFuture.failedFuture()` 传播

### 7.4 鉴权流程（阿里云 NLS）

1. 使用配置中的 `AccessKeyId` + `AccessKeySecret` 向 `POST <endpoint>/token` 换取 NLS Token（24 小时有效）
2. Token 缓存于内存，提前 **60 秒**自动刷新（`synchronized` 保证线程安全）
3. 每次合成请求将 Token 与 AppKey 附在 URL Query 参数中（阿里云 NLS 协议要求，URL 本身禁止打印到日志）
4. 成功响应：HTTP 200，Body 为二进制音频
5. 失败响应：HTTP 4xx/5xx，异常包装为 `TtsException`

### 7.5 TTS 错误码

| 错误码 | 常量 | 触发场景 |
|---|---|---|
| `40011` | `TTS_INVALID_REQUEST` | 文本为空或超过 1000 字符 |
| `50211` | `TTS_TOKEN_FETCH_FAILED` | Token 获取失败（网络/Key 错误） |
| `50212` | `TTS_SYNTHESIS_FAILED` | NLS 服务端 4xx/5xx |

### 7.6 配置项（application.yml / 环境变量）

| 配置键 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `tts.aliyun.app-key` | `TTS_ALIYUN_APP_KEY` | — | NLS 项目 AppKey（必填） |
| `tts.aliyun.access-key-id` | `TTS_ALIYUN_ACCESS_KEY_ID` | — | RAM AccessKeyId（必填） |
| `tts.aliyun.access-key-secret` | `TTS_ALIYUN_ACCESS_KEY_SECRET` | — | RAM AccessKeySecret（必填） |
| `tts.aliyun.endpoint` | — | `https://nls-gateway.cn-shanghai.aliyuncs.com` | NLS 网关地址 |
| `tts.aliyun.voice` | — | `aixia` | 默认发音人 |
| `tts.aliyun.sample-rate` | — | `16000` | 默认采样率（Hz） |
| `tts.aliyun.format` | — | `wav` | 默认音频格式 |
| `tts.provider` | — | `ALIYUN` | 云厂商枚举（`TtsProvider`） |

> **安全规约**：`app-key`、`access-key-id`、`access-key-secret` 必须通过环境变量注入，禁止明文提交到代码仓库。

---

## 8. 典型业务流程

### 7.1 课件上传与解析
1. 前端调用 `/api/v1/courseware/upload`
2. 后端保存文件并创建课件记录
3. 前端或后端触发 `/api/v1/courseware/parse`
4. 后端调用 `/python/v1/parse`
5. Python 返回解析摘要
6. 后端更新课件状态并触发脚本生成、向量化等后续流程

### 7.2 讲课中断与恢复
1. 用户发起语音或文本提问
2. 后端将会话状态更新为 `INTERRUPTED`
3. 后端调用 Python 问答链路
4. 返回问答结果并生成恢复令牌
5. 前端调用 `/api/v1/lecture/resume`
6. 会话恢复到原节点继续讲解

---

## 8. 联调要求

### 8.1 前后端联调
- 接口字段变更先更新本文件
- 前端联调前确认：
  - 路径
  - 方法
  - 请求格式
  - 状态字段
  - 错误码
- 前端不得自行推断未文档化的字段含义

### 8.2 后端与 Python 服务联调
- 统一超时、重试和日志追踪策略
- 对模型类返回值必须做结构化校验
- 内部接口即使不直接暴露给前端，也必须保持响应结构统一

---

## 9. 文档维护要求

- 新增接口时同步补充本文件
- 调整字段、状态值、错误码时同步修改示例
- 关键流程发生变化时同步更新“典型业务流程”章节
- 如果实际代码中接口路径与本文档不一致，以修正文档和代码其中之一的方式尽快收敛，不允许长期漂移
