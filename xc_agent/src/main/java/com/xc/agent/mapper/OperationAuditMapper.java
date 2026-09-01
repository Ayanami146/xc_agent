package com.xc.agent.mapper;

import com.xc.agent.model.po.OperationAuditPO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

public interface OperationAuditMapper {
    int insert(OperationAuditPO audit);
    List<OperationAuditPO> selectPage(@Param("operatorId") String operatorId,
                                      @Param("resourceType") String resourceType,
                                      @Param("action") String action,
                                      @Param("requestId") String requestId,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to,
                                      @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countPage(@Param("operatorId") String operatorId,
                   @Param("resourceType") String resourceType,
                   @Param("action") String action,
                   @Param("requestId") String requestId,
                   @Param("from") LocalDate from, @Param("to") LocalDate to);
}
