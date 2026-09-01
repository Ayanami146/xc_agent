"""永久上下文冷恢复测试。"""

from types import SimpleNamespace
from typing import Any, cast

from agent_service.services.context_archive import ContextArchive


class FakeStore:
    """只实现测试所需的 asearch，验证恢复逻辑不会依赖数据库返回顺序。"""

    def __init__(self) -> None:
        self.requested_limit: int | None = None

    async def asearch(self, namespace: tuple[str, ...], *, limit: int) -> list[Any]:
        del namespace
        self.requested_limit = limit
        # 故意乱序，模拟 MongoDB 在没有显式 sort 时的合法返回结果。
        return [
            SimpleNamespace(
                value={
                    "created_at": "2026-08-29T03:00:00+00:00",
                    "request_id": 3,
                    "user_text": "u3",
                    "assistant_text": "a3",
                }
            ),
            SimpleNamespace(
                value={
                    "created_at": "2026-08-29T01:00:00+00:00",
                    "request_id": 1,
                    "user_text": "u1",
                    "assistant_text": "a1",
                }
            ),
            SimpleNamespace(
                value={
                    "created_at": "2026-08-29T02:00:00+00:00",
                    "request_id": 2,
                    "user_text": "u2",
                    "assistant_text": "a2",
                }
            ),
        ]


async def test_rehydrate_selects_latest_turns_after_sorting() -> None:
    store = FakeStore()
    archive = ContextArchive(
        # 本测试只走 MongoDB 恢复路径，不会访问 Redis。
        redis=cast(Any, object()),
        store=cast(Any, store),
        stream_name="unused",
        rehydrate_turn_limit=2,
    )

    messages = await archive.load_messages(user_id=1, session_id=2)

    # 必须先获取全部候选项再排序，否则 MongoDB 的无序 limit 可能留下旧轮次。
    assert store.requested_limit == 0
    assert [message.content for message in messages] == ["u2", "a2", "u3", "a3"]
