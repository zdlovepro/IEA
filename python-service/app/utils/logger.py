"""
日志工具
========
统一日志配置，遵照规约 3.4：使用 logging 模块，输出到控制台及文件，
关键接口记录请求耗时。

保存位置：python-service/app/utils/logger.py
"""

import logging
import sys
from pathlib import Path


def setup_logger(name: str = "ai-parser") -> logging.Logger:
    """
    创建并返回一个配置好的 Logger 实例。

    Parameters
    ----------
    name : str
        Logger 名称，默认 "ai-parser"。

    Returns
    -------
    logging.Logger
        配置好的日志实例，同时输出到控制台和文件。
    """
    _logger = logging.getLogger(name)

    # 防止重复添加 handler（模块被多次 import 时）
    if _logger.handlers:
        return _logger

    _logger.setLevel(logging.DEBUG)

    # ---- 日志格式 ----
    formatter = logging.Formatter(
        fmt="%(asctime)s | %(levelname)-8s | %(name)s | %(module)s:%(lineno)d | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # ---- 控制台输出 ----
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    _logger.addHandler(console_handler)

    # ---- 文件输出（日志文件存放在 python-service/logs/ 下） ----
    log_dir = Path(__file__).resolve().parent.parent.parent / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    file_handler = logging.FileHandler(
        log_dir / "parser.log", encoding="utf-8"
    )
    file_handler.setFormatter(formatter)
    _logger.addHandler(file_handler)

    return _logger


# 全局 logger 实例，其他模块直接 from app.utils.logger import logger
logger = setup_logger()
