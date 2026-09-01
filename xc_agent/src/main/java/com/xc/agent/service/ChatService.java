package com.xc.agent.service;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.model.dto.chat.ChatDTOs;
import com.xc.agent.model.vo.chat.ChatVOs;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户聊天会话业务接口。
 *
 * <p>本接口中的 {@code sessionId} 统一为 chat_session.id 自增主键。</p>
 */
public interface ChatService {
    /**
     * 查询当前登录用户的会话分页数据。
     */
    PageVO<ChatVOs.ChatSessionVO> listSessions(ChatDTOs.SessionQueryDTO query);

    /**
     * 按会话主键修改当前登录用户的会话标题。
     */
    ChatVOs.ChatSessionVO renameSession(Long sessionId, ChatDTOs.SessionRenameDTO dto);

    /**
     * 按会话主键软删除当前登录用户的会话。
     */
    void deleteSession(Long sessionId);

    /**
     * 查询历史消息
     */
    PageVO<ChatVOs.ChatMessageVO> listMessages(Long sessionId, ChatDTOs.MessageQueryDTO query);

    /** 创建聊天记录并立即返回由后台虚拟线程持续写入的 SSE。 */
    SseEmitter startStream(ChatDTOs.ChatStreamDTO dto);

    /** 查询当前用户的一次聊天请求，供断流或刷新页面后恢复。 */
    ChatVOs.ChatRequestResultVO getRequestResult(Long requestId);

    /** 幂等取消运行中的请求，并把取消信号传播到 Python Agent。 */
    ChatVOs.ChatRequestResultVO cancelRequest(Long requestId);

    /** 设置或清除当前用户对助手消息的反馈。 */
    void saveFeedback(Long messageId, ChatDTOs.MessageFeedbackDTO dto);
}
