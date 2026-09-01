package com.xc.agent.model.po;

import com.xc.agent.model.enums.ChatEnums;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 历史消息列表的数据库查询结果。
 *
 * <p>引用列表不参与主查询，由业务层按需补充，避免一对多联查破坏分页。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageListRow {
    private Long id;
    private Long requestId;
    private ChatEnums.MessageRole role;
    private String content;
    private ChatEnums.MessageStatus status;
    private LocalDateTime createdAt;
    private ChatEnums.Stage stage;
    private ChatEnums.Feedback feedback;
}
