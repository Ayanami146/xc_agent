import asyncio
import json
import time

import httpx

URL = "http://127.0.0.1:8100/internal/ai/v1/chat/stream"

# 如果开启了 AGENT_INTERNAL_AUTH_ENABLED，则填写内部 Token。
HEADERS = {
    # "X-Internal-Token": "请填写实际 Token",
}


def request_body(request_id: int, session_id: int) -> dict:
    return {
        "requestId": request_id,
        "sessionId": session_id,
        "userId": 1,
        "message": "请结合维修手册详细说明银河麒麟系统浏览器兼容性问题。",
        "history": [],
        "policy": {
            "modelRoute": "default",
            "knowledgeBaseIds": ["default"],
            "toolsEnabled": False,
            "maxOutputTokens": 512,
        },
    }


async def send_request(
    client: httpx.AsyncClient,
    request_id: int,
    session_id: int,
) -> dict:
    started_at = time.perf_counter()
    events = []

    async with client.stream(
        "POST",
        URL,
        headers=HEADERS,
        json=request_body(request_id, session_id),
        timeout=180,
    ) as response:
        async for line in response.aiter_lines():
            if line.startswith("data: "):
                events.append(json.loads(line.removeprefix("data: ")))

    elapsed = time.perf_counter() - started_at
    terminal = events[-1] if events else None

    return {
        "request_id": request_id,
        "session_id": session_id,
        "elapsed": round(elapsed, 2),
        "terminal": terminal,
    }


async def test_different_sessions() -> None:
    """不同会话应当可以并发完成。"""

    async with httpx.AsyncClient() as client:
        started_at = time.perf_counter()

        results = await asyncio.gather(
            *[
                send_request(
                    client,
                    request_id=10000 + index,
                    session_id=20000 + index,
                )
                for index in range(5)
            ]
        )

        total_elapsed = time.perf_counter() - started_at

    print("\n不同会话并发测试：")
    print(f"总耗时：{total_elapsed:.2f} 秒")
    for result in results:
        print(result)


async def test_same_session() -> None:
    """同一会话的第二个请求应返回并发冲突。"""

    async with httpx.AsyncClient() as client:
        results = await asyncio.gather(
            send_request(client, request_id=30001, session_id=40001),
            send_request(client, request_id=30002, session_id=40001),
        )

    print("\n同一会话并发测试：")
    for result in results:
        print(result)


async def main() -> None:
    await test_different_sessions()
    await test_same_session()


if __name__ == "__main__":
    asyncio.run(main())