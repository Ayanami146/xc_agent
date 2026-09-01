package com.xc.agent.mapper;

import com.xc.agent.model.po.TicketAttachmentPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TicketAttachmentMapper {
    List<TicketAttachmentPO> selectByTicketId(@Param("ticketId") Long ticketId);
}
