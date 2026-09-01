"""Server-Sent Events 序列化。"""

from agent_service.schemas.chat import SseEnvelope


def encode_sse(envelope: SseEnvelope) -> str:
    """将事件信封编码成浏览器和 Java 客户端都能解析的 SSE 帧。

    每个帧以空行结束；``id`` 使用严格递增的 sequence，便于后续增加断线续传。
    JSON 使用 camelCase，并保留中文字符以方便排查日志和抓包。
    """

    data = envelope.model_dump_json(by_alias=True, exclude_none=True)
    return f"event: {envelope.event}\nid: {envelope.sequence}\ndata: {data}\n\n"
