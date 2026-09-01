package com.xc.agent.common.api;

import java.util.List;

public record PageVO<T>(List<T> items, long total, int page, int pageSize) {
}
