package com.xc.agent.mapper;

import java.util.List;
import java.util.Map;

public interface AdminDashboardMapper {
    Map<String, Long> selectMetrics();
    List<Map<String, Object>> selectTicketStatus();
    List<Map<String, Object>> selectTicketTrend();
}
