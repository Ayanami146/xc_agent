"""进程内运行注册表与取消令牌。

最小骨架先使用内存保存活动运行，便于验证完整取消流程。多实例部署时必须替换成 Redis，
否则取消请求可能被负载均衡到另一实例而无法命中原运行。
"""

import asyncio

from agent_service.core.exceptions import RunAlreadyActiveError


class CancellationRegistry:
    """以 requestId 管理活动运行及其取消事件。"""

    def __init__(self) -> None:
        self._runs: dict[int, asyncio.Event] = {}
        self._lock = asyncio.Lock()

    async def register(self, request_id: int) -> None:
        """注册一条新运行；重复的活动 requestId 会被拒绝。"""

        async with self._lock:
            if request_id in self._runs:
                raise RunAlreadyActiveError(f"requestId={request_id} 已在运行")
            self._runs[request_id] = asyncio.Event()

    async def cancel(self, request_id: int) -> bool:
        """设置取消标记，并返回是否命中了活动运行。

        接口采用幂等语义：未运行或已经结束时返回 ``False``，而不是抛出异常。
        """

        async with self._lock:
            event = self._runs.get(request_id)
            if event is None:
                return False
            event.set()
            return True

    async def is_cancelled(self, request_id: int) -> bool:
        """查询指定运行是否已经收到取消请求。"""

        async with self._lock:
            event = self._runs.get(request_id)
            return event.is_set() if event is not None else False

    async def finish(self, request_id: int) -> None:
        """运行进入终态后释放内存记录。"""

        async with self._lock:
            self._runs.pop(request_id, None)
