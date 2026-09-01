"""智能体聊天和取消接口。"""

import logging
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, HTTPException, Path, Request, status
from fastapi.responses import StreamingResponse

from agent_service.api.dependencies import verify_internal_token
from agent_service.core.exceptions import (
    AgentCancelledError,
    ContextArchiveUnavailableError,
    ContextStoreUnavailableError,
    ContextTooLargeError,
    EmptyModelOutputError,
    RoleConfigurationError,
    RunAlreadyActiveError,
)
from agent_service.schemas.chat import CancelResponse, ChatStreamRequest, SseEnvelope
from agent_service.services.agent_runtime import AgentRuntime
from agent_service.streaming.sse import encode_sse

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/chat",
    tags=["chat"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/stream", response_class=StreamingResponse)
async def stream_chat(body: ChatStreamRequest, request: Request) -> StreamingResponse:
    """执行一轮智能体并以 SSE 逐步返回事件。

    HTTP 响应建立后，运行期错误必须通过终态 ``error`` 事件发送，不能再改 HTTP 状态码。
    """

    runtime: AgentRuntime = request.app.state.agent_runtime
    try:
        # 在建立 SSE 响应前读取角色，配置错误可以用明确的 HTTP 状态返回。
        active_role = runtime.load_active_role()
    except RoleConfigurationError as exc:
        logger.exception("活动角色配置加载失败")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"code": "ROLE_CONFIG_INVALID", "message": "后端活动角色配置无效"},
        ) from exc

    async def event_generator() -> AsyncIterator[str]:
        sequence = 1

        def frame(event: str, payload: dict[str, object]) -> str:
            nonlocal sequence
            envelope = SseEnvelope.create(
                event=event,
                request_id=body.request_id,
                sequence=sequence,
                payload=payload,
            )
            sequence += 1
            return encode_sse(envelope)

        # Python Agent 不知道 MySQL 消息主键；Java 将在对外 SSE 中补充这些字段。
        yield frame(
            "meta",
            {"sessionId": body.session_id, "roleName": active_role.name},
        )

        try:
            async for agent_event in runtime.stream(body, active_role):
                # 客户端断开时停止继续消费模型流；finally 会清理运行注册信息。
                if await request.is_disconnected():
                    await runtime.cancel(body.request_id)
                    return
                yield frame(agent_event.event, agent_event.payload)

            yield frame(
                "done",
                {
                    "finishReason": "stop",
                    "model": runtime.model_gateway.model_name,
                    "roleName": active_role.name,
                },
            )
        except RunAlreadyActiveError:
            yield frame(
                "error",
                {
                    "code": "SESSION_RUN_ALREADY_ACTIVE",
                    "message": "该会话已有请求正在执行",
                    "retryable": True,
                },
            )
        except ContextArchiveUnavailableError:
            yield frame(
                "error",
                {
                    "code": "CONTEXT_ARCHIVE_UNAVAILABLE",
                    "message": "上下文归档通道暂时不可用",
                    "retryable": True,
                },
            )
        except ContextStoreUnavailableError:
            yield frame(
                "error",
                {
                    "code": "CONTEXT_STORE_UNAVAILABLE",
                    "message": "历史上下文暂时无法读取",
                    "retryable": True,
                },
            )
        except ContextTooLargeError:
            yield frame(
                "error",
                {
                    "code": "CONTEXT_TOO_LARGE",
                    "message": "本轮输入超过模型上下文上限",
                    "retryable": False,
                },
            )
        except AgentCancelledError:
            yield frame(
                "error",
                {
                    "code": "RUN_CANCELLED",
                    "message": "智能体运行已取消",
                    "retryable": False,
                },
            )
        except EmptyModelOutputError:
            yield frame(
                "error",
                {
                    "code": "OUTPUT_VALIDATION_FAILED",
                    "message": "模型未生成有效回答",
                    "retryable": True,
                },
            )
        except Exception:
            # 日志记录异常堆栈，但不把内部地址、密钥或供应商异常原文暴露给 Java/浏览器。
            logger.exception("智能体运行发生未处理异常，requestId=%s", body.request_id)
            yield frame(
                "error",
                {
                    "code": "MODEL_UNAVAILABLE",
                    "message": "模型服务暂时不可用",
                    "retryable": True,
                },
            )

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/requests/{requestId}/cancel", response_model=CancelResponse)
async def cancel_chat(
    request: Request,
    request_id: int = Path(gt=0, alias="requestId"),
) -> CancelResponse:
    """幂等地请求取消一条活动运行。"""

    runtime: AgentRuntime = request.app.state.agent_runtime
    cancel_requested = await runtime.cancel(request_id)
    return CancelResponse(
        request_id=request_id,
        cancel_requested=cancel_requested,
        status="CANCEL_REQUESTED" if cancel_requested else "NOT_RUNNING",
    )
