package com.xc.agent.mapper;

import com.xc.agent.model.po.ChatSessionPO;
import com.xc.agent.model.vo.chat.ChatVOs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * chat_session 表的会话查询和维护操作。
 */
@Mapper
public interface ChatSessionMapper {
    /** 创建新会话，并把数据库生成的自增主键回填到 session.id。 */
    int insert(ChatSessionPO session);

    /** 分页查询指定用户尚未软删除的会话。 */
    List<ChatVOs.ChatSessionVO> list(@Param("offset") int offset,
                                     @Param("pageSize") int pageSize,
                                     @Param("keyword") String keyword,
                                     @Param("userId") Long userId);

    /** 统计与 list 相同条件下的会话总数。 */
    long count(@Param("keyword") String keyword,
               @Param("userId") Long userId);

    /** 按会话主键和所有者修改标题，返回受影响行数。 */
    int rename(@Param("sessionId") Long sessionId,
               @Param("userId") Long userId,
               @Param("title") String title);

    /** 按会话主键和所有者查询一条未删除会话。 */
    ChatVOs.ChatSessionVO selectByIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    /** 写入 deleted_at 完成软删除，返回受影响行数。 */
    int softDelete(@Param("sessionId") Long sessionId,
                   @Param("userId") Long userId);

    /** 新问题进入会话时更新列表预览和最后消息时间。 */
    int updatePreview(@Param("sessionId") Long sessionId,
                      @Param("userId") Long userId,
                      @Param("preview") String preview);
}
