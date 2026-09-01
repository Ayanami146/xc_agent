package com.xc.agent.mapper;

import com.xc.agent.model.po.TicketPO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TicketMapper {
    List<TicketPO> selectAdminPage(@Param("keyword") String keyword, @Param("status") String status,
                                   @Param("assigneeId") Long assigneeId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to, @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);
    long countAdminPage(@Param("keyword") String keyword, @Param("status") String status,
                        @Param("assigneeId") Long assigneeId, @Param("from") LocalDate from,
                        @Param("to") LocalDate to);
    TicketPO selectByPublicId(@Param("publicId") String publicId);
    int assign(@Param("id") Long id, @Param("assigneeId") Long assigneeId,
               @Param("expectedVersion") int expectedVersion);
    int transition(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus, @Param("resolution") String resolution,
                   @Param("resolvedAt") LocalDateTime resolvedAt,
                   @Param("closedAt") LocalDateTime closedAt,
                   @Param("expectedVersion") int expectedVersion);
}
