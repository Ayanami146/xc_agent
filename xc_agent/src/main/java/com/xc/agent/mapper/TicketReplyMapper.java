package com.xc.agent.mapper;

import com.xc.agent.model.po.TicketReplyPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TicketReplyMapper {
    List<TicketReplyPO> selectByTicketId(@Param("ticketId") Long ticketId);
    int insert(TicketReplyPO reply);
}
