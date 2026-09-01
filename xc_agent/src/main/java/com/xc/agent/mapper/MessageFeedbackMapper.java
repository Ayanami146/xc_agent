package com.xc.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 当前用户对助手消息的赞踩写入。 */
@Mapper
public interface MessageFeedbackMapper {
    int upsert(@Param("messageId") Long messageId,
               @Param("userId") Long userId,
               @Param("feedback") String feedback);

    int delete(@Param("messageId") Long messageId,
               @Param("userId") Long userId);
}
