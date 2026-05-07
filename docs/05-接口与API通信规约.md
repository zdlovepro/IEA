# 05-接口与 API 通信规约

本文档用于统一项目对外接口、内部服务调用接口、状态字段和错误码约定。任何接口契约变更都应先同步更新本文档。

## 1. 通用约定

### 1.1 基础路径

- 外部网关接口：`/api/v1`
- Python 内部服务接口：`/python/v1`

### 1.2 统一响应格式

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

### 1.3 公共请求头

- `Authorization: Bearer <token>`：需要登录态时使用
- `X-Trace-Id: <traceId>`：推荐透传链路 ID

## 2. 状态字段

### 2.1 课件状态

- `UPLOADED`：已上传
- `PARSING`：解析中
- `PARSED`：解析完成
- `GENERATING_SCRIPT`：讲稿生成中
- `READY`：可开始讲课
- `FAILED`：处理失败

### 2.2 讲课会话状态

- `IDLE`：未开始
- `PLAYING`：讲解中
- `INTERRUPTED`：被打断
- `ANSWERING`：问答中
- `RESUMING`：恢复讲课中
- `ENDED`：已结束

## 3. 通用错误码

| 错误码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `40001` | 参数缺失或格式错误 |
| `40002` | 业务校验失败 |
| `40101` | 未登录或 Token 无效 |
| `40301` | 无权限访问 |
| `40401` | 资源不存在 |
| `40901` | 状态冲突或重复操作 |
| `50001` | 服务内部错误 |
| `50101` | 功能暂未实现 |
| `50201` | 下游 Python 服务异常 |
| `50202` | 第三方模型或媒体服务异常 |

## 4. 核心接口示例

### 4.1 开始讲课

- 路径：`POST /api/v1/lecture/start`

请求：

```json
{
  "coursewareId": "cware_123456",
  "userId": "user_001"
}
```

响应：

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

说明：

- `audioUrl` 在 `tts.enabled=false` 时可以为 `null`
- `audioUrl` 在 TTS 合成或音频保存失败时也可以为 `null`
- `tts.enabled=true` 且密钥和存储可用时，后端会在讲稿生成时为片段补充 `audioUrl`

### 4.2 文本问答

- 路径：`POST /api/v1/qa/ask-text`

响应：

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

### 4.3 Python 课件解析内部接口

- 路径：`POST /python/v1/parse`
- 调用方：仅 Java 后端调用，前端不直连 Python 服务

请求：

```json
{
  "coursewareId": "cware_123456",
  "storage": "local",
  "key": "cware_123456/递归示例.pptx",
  "fileName": "递归示例.pptx",
  "contentType": "application/vnd.openxmlformats-officedocument.presentationml.presentation"
}
```

字段说明：

- `coursewareId`：课件 ID
- `storage`：存储类型，当前约定 `local` 或 `minio`
- `key`：对象 key 或本地相对路径
- `fileName`：原始文件名
- `contentType`：文件 MIME 类型

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "pages": 3,
    "outline": [
      "课程导入",
      "核心概念",
      "总结回顾"
    ],
    "segments": [
      {
        "pageIndex": 1,
        "title": "课程导入",
        "content": "本页主要介绍课程背景和学习目标。",
        "knowledgePoints": [
          "递归示例",
          "课程导入"
        ]
      }
    ]
  }
}
```

行为约定：

- `storage=local` 时，Python 从 `backend/data/courseware` 下按 `key` 查找文件并解析
- `storage=minio` 时，当前版本返回明确的未实现错误，不静默成功

### 4.4 TTS 行为约定

- `tts.enabled=false` 时，不初始化真实阿里云 TTS 调用，主流程继续返回文本讲稿
- `tts.enabled=true` 且密钥可用时，讲稿片段生成后会尝试生成 `audioUrl`
- `storage=local` 时，`audioUrl` 形如 `/api/v1/tts/audio/tts-audio/...`
- `storage=minio` 时，`audioUrl` 形如 MinIO 预签名地址
- TTS 合成或音频上传失败时，只降级为 `audioUrl=null`，不应导致课件解析或讲稿生成整体失败

## 5. 典型业务流程

### 5.1 课件上传与解析

1. 前端调用 `POST /api/v1/courseware/upload`
2. 后端保存文件并创建课件记录
3. 后端触发解析流程
4. 后端调用 `POST /python/v1/parse`
5. Python 返回解析摘要
6. 后端更新课件状态并触发后续流程

### 5.2 讲课中断与恢复

1. 用户发起语音或文本提问
2. 后端将会话状态更新为 `INTERRUPTED`
3. 后端调用 Python 问答链路
4. 返回问答结果并生成恢复指令
5. 前端调用 `POST /api/v1/lecture/resume`
6. 会话恢复到原节点继续讲解
