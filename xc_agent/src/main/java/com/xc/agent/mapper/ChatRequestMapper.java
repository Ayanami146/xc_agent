package com.xc.agent.mapper;

import com.xc.agent.model.po.ChatRequestPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 一次聊天运行的状态读写。所有查询都必须同时限制 user_id。 */
@Mapper
public interface ChatRequestMapper {
    int insert(ChatRequestPO request);

    ChatRequestPO selectByIdAndUserId(@Param("requestId") Long requestId,
                                      @Param("userId") Long userId);

    int markRunning(@Param("requestId") Long requestId,
                    @Param("userId") Long userId);

    int markSucceeded(@Param("requestId") Long requestId,
                      @Param("userId") Long userId,
                      @Param("modelRoute") String modelRoute,
                      @Param("usageJson") String usageJson);

    int markFailed(@Param("requestId") Long requestId,
                   @Param("userId") Long userId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    int markCancelled(@Param("requestId") Long requestId,
                      @Param("userId") Long userId);
}
