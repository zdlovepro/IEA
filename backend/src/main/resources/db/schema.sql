-- 1. 课件表
CREATE TABLE IF NOT EXISTS courseware (
    id VARCHAR(64) PRIMARY KEY COMMENT '课件唯一ID，如 cware_xxxxx',
    name VARCHAR(255) NOT NULL COMMENT '课件名称',
    file_url VARCHAR(512) NOT NULL COMMENT '在 MinIO 中的存储路径/URL',
    file_type VARCHAR(32) NOT NULL COMMENT '文件类型：PDF/PPTX 等',
    status VARCHAR(64) NOT NULL DEFAULT 'UPLOADED' COMMENT '状态：UPLOADED, PARSING, PARSED, GENERATING, READY, FAILED',
    uploader_id VARCHAR(64) COMMENT '上传教师ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课件基础信息表';

-- 2. 课件解析页面表
CREATE TABLE IF NOT EXISTS courseware_page (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    courseware_id VARCHAR(64) NOT NULL COMMENT '关联课件ID',
    page_index INT NOT NULL COMMENT '页码，从1开始',
    original_text TEXT COMMENT '通过 Python 解析引擎提取的原文',
    image_url VARCHAR(512) COMMENT '该页的截图/截图地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_cw_page (courseware_id, page_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课件分页解析内容表';

-- 3. 讲稿脚本表
CREATE TABLE IF NOT EXISTS lecture_script (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    courseware_id VARCHAR(64) NOT NULL COMMENT '关联课件ID',
    page_index INT NOT NULL COMMENT '所在页码',
    node_id VARCHAR(64) NOT NULL COMMENT '讲授片段节点ID，例如 node_001',
    content TEXT COMMENT '由大模型生成的讲授文本脚本',
    audio_url VARCHAR(512) COMMENT 'TTS 语音播报地址',
    edit_status VARCHAR(32) DEFAULT 'AUTO' COMMENT '编辑状态：AUTO(AI生成), EDITED(教师修改过)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_node_id (node_id),
    INDEX idx_cw_script (courseware_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化讲稿表';

-- 4. 问答记录表
CREATE TABLE IF NOT EXISTS qa_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id VARCHAR(128) NOT NULL COMMENT '讲课交互会话ID',
    courseware_id VARCHAR(64) NOT NULL COMMENT '关联的课件ID',
    node_id VARCHAR(64) COMMENT '当前打断在哪一个节点ID',
    user_id VARCHAR(64) NOT NULL COMMENT '提问的用户(学生)ID',
    ask_text TEXT NOT NULL COMMENT '学生的提问内容',
    answer_text TEXT COMMENT '大模型的回答内容',
    reference_fragments JSON COMMENT '引用的溯源课件片段',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '产生时间',
    INDEX idx_session_cw (session_id, courseware_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时问答交互记录表';

-- 5. 讲课会话与进度表
CREATE TABLE IF NOT EXISTS lecture_session (
    id VARCHAR(128) PRIMARY KEY COMMENT '会话ID，如 sess_xxxxx',
    courseware_id VARCHAR(64) NOT NULL COMMENT '正在学习的课件ID',
    user_id VARCHAR(64) NOT NULL COMMENT '学习该课件的学生/用户ID',
    current_page_index INT DEFAULT 1 COMMENT '当前播放到的页码',
    current_node_id VARCHAR(64) COMMENT '当前播放/打断的具体节点ID',
    resume_token VARCHAR(255) COMMENT '恢复讲课所需的续接 Token',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE(进行中), PAUSED(暂停), FINISHED(已完成)',
    understanding_level VARCHAR(32) DEFAULT 'NORMAL' COMMENT 'AI判定的学生理解度：POOR, NORMAL, GOOD (用于动态调速)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次进度更新时间',
    INDEX idx_user_cw (user_id, courseware_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讲课交互会话与学习进度表';

-- 6. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(64) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(128) NOT NULL COMMENT '用户名/学号/工号',
    password VARCHAR(255) NOT NULL COMMENT '密码哈希',
    real_name VARCHAR(64) COMMENT '真实姓名',
    role VARCHAR(32) NOT NULL DEFAULT 'STUDENT' COMMENT '角色：TEACHER, STUDENT, ADMIN',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';