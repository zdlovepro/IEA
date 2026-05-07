from __future__ import annotations

import logging
import sys
from pathlib import Path


def setup_logger(name: str = "python-service") -> logging.Logger:
    configured_logger = logging.getLogger(name)
    if configured_logger.handlers:
        return configured_logger

    configured_logger.setLevel(logging.INFO)
    configured_logger.propagate = False

    formatter = logging.Formatter(
        fmt="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    configured_logger.addHandler(console_handler)

    log_dir = Path(__file__).resolve().parent.parent.parent / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    file_handler = logging.FileHandler(log_dir / "python-service.log", encoding="utf-8")
    file_handler.setFormatter(formatter)
    configured_logger.addHandler(file_handler)

    return configured_logger


logger = setup_logger()
