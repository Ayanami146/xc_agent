"""活动角色文件读取服务。"""

import json
from pathlib import Path

from pydantic import ValidationError

from agent_service.core.exceptions import RoleConfigurationError
from agent_service.schemas.role import RoleProfile


class RoleProfileProvider:
    """在每次新 Agent 运行前读取后端活动角色。

    个人使用场景的角色文件很小，逐次读取比实现文件监听、缓存失效和管理接口更简单可靠。
    已经开始的运行继续使用自己的角色快照；文件修改只影响之后的新运行。
    """

    def __init__(self, config_path: Path) -> None:
        self._config_path = config_path

    @property
    def config_path(self) -> Path:
        """返回当前配置文件位置，便于健康检查和问题诊断。"""

        return self._config_path

    def load(self) -> RoleProfile:
        """读取并校验当前活动角色。"""

        try:
            raw_text = self._config_path.read_text(encoding="utf-8")
            raw_data = json.loads(raw_text)
            return RoleProfile.model_validate(raw_data)
        except FileNotFoundError as exc:
            raise RoleConfigurationError(f"活动角色文件不存在：{self._config_path}") from exc
        except json.JSONDecodeError as exc:
            raise RoleConfigurationError(f"活动角色文件不是合法 JSON：{self._config_path}") from exc
        except ValidationError as exc:
            raise RoleConfigurationError(f"活动角色文件字段校验失败：{self._config_path}") from exc
