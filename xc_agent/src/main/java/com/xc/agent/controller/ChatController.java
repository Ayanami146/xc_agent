package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.model.dto.chat.ChatDTOs;
import com.xc.agent.model.vo.chat.ChatVOs;
import com.xc.agent.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户端聊天会话接口。
 *
 * <p>路径中的 {@code sessionId} 统一对应 {@code chat_session.id}，
 * 直接使用数据库自增主键。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 分页查询当前登录用户未删除的会话。
     *
     * @param query 分页和关键字查询条件
     * @param request 用于取得本次请求的 requestId
     * @return 当前用户的会话分页数据
     */
    @GetMapping("/sessions")
    public ApiResponse<PageVO<ChatVOs.ChatSessionVO>> listSessions(
            @Valid @ModelAttribute ChatDTOs.SessionQueryDTO query,
            HttpServletRequest request) {
        return response(request, chatService.listSessions(query));
    }

    /**
     * 修改当前登录用户指定会话的标题。
     *
     * @param sessionId 会话主键，对应 chat_session.id
     * @param dto 新标题
     * @param request 用于取得本次请求的 requestId
     * @return 修改后的会话
     */
    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<ChatVOs.ChatSessionVO> renameSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatDTOs.SessionRenameDTO dto,
            HttpServletRequest request) {
        return response(request, chatService.renameSession(sessionId, dto));
    }

    /**
     * 软删除当前登录用户的会话。
     *
     * <p>软删除只写入 deleted_at，不会物理删除会话、请求和消息记录。</p>
     *
     * @param sessionId 会话主键，对应 chat_session.id
     * @return 204 No Content
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<PageVO<ChatVOs.ChatMessageVO>> listMessages(
            @PathVariable Long sessionId,
            @Valid @ModelAttribute ChatDTOs.MessageQueryDTO query,
            HttpServletRequest request) {
        return response(request, chatService.listMessages(sessionId, query));
    }

    /**
     * 发起流式聊天。SseEmitter 会立即交还给容器，Agent 调用在后台虚拟线程运行。
     *
     * <p>禁用代理缓冲非常重要：若 Nginx/Vite 把响应片段攒到连接结束才转发，数据库
     * 虽然会正常落库，但浏览器在生成期间只能一直看到“正在分析问题”。</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatDTOs.ChatStreamDTO dto,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return chatService.startStream(dto);
    }

    @GetMapping("/chat/requests/{requestId}")
    public ApiResponse<ChatVOs.ChatRequestResultVO> getRequestResult(
            @PathVariable Long requestId,
            HttpServletRequest request) {
        return response(request, chatService.getRequestResult(requestId));
    }

    @PostMapping("/chat/requests/{requestId}/cancel")
    public ApiResponse<ChatVOs.ChatRequestResultVO> cancelRequest(
            @PathVariable Long requestId,
            HttpServletRequest request) {
        return response(request, chatService.cancelRequest(requestId));
    }

    @PutMapping("/messages/{messageId}/feedback")
    public ResponseEntity<Void> saveFeedback(
            @PathVariable Long messageId,
            @Valid @RequestBody ChatDTOs.MessageFeedbackDTO dto) {
        chatService.saveFeedback(messageId, dto);
        return ResponseEntity.noContent().build();
    }

    private <T> ApiResponse<T> response(HttpServletRequest request, T data) {
        // 所有普通 JSON 响应都带上 requestId，便于与后端日志关联。
        return new ApiResponse<>(RequestIdFilter.get(request), data);
    }
}
