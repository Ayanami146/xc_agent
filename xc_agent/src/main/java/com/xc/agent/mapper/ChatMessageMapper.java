package com.xc.agent.mapper;

import com.xc.agent.model.po.ChatMessageListRow;
import com.xc.agent.model.po.ChatMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** chat_message 历史消息分页查询。 */
@Mapper
public interface ChatMessageMapper {
    int insert(ChatMessagePO message);

    List<ChatMessageListRow> list(@Param("offset") int offset,
                                  @Param("pageSize") int pageSize,
                                  @Param("sessionId") Long sessionId,
                                  @Param("userId") Long userId);

    long count(@Param("sessionId") Long sessionId);

    ChatMessagePO selectAssistantByRequestIdAndUserId(
            @Param("requestId") Long requestId,
            @Param("userId") Long userId);

    ChatMessagePO selectOwnedAssistantById(@Param("messageId") Long messageId,
                                           @Param("userId") Long userId);

    int updateCompleted(@Param("messageId") Long messageId,
                        @Param("content") String content,
                        @Param("modelName") String modelName,
                        @Param("tokenCount") Integer tokenCount);

    int updateFailed(@Param("messageId") Long messageId,
                     @Param("content") String content);

    int updateInterruptedByRequestId(@Param("requestId") Long requestId,
                                     @Param("userId") Long userId);
}
