package com.xc.agent.mapper;

import com.xc.agent.model.po.TicketStatusHistoryPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TicketStatusHistoryMapper {
    List<TicketStatusHistoryPO> selectByTicketId(@Param("ticketId") Long ticketId);
    int insert(TicketStatusHistoryPO history);
}
