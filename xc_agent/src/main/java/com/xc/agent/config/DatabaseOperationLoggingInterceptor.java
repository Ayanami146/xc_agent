package com.xc.agent.config;

import com.xc.agent.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

@Component
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DatabaseOperationLoggingInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        long startedAt = System.nanoTime();
        try {
            Object result = invocation.proceed();
            long elapsedMillis = elapsedMillis(startedAt);
            log.info("DB {} {} -> rows={} ({} ms), requestId={}",
                    statement.getSqlCommandType(), statement.getId(), affectedRows(result),
                    elapsedMillis, requestId());
            return result;
        } catch (Throwable exception) {
            Throwable cause = rootCause(exception);
            log.error("DB {} {} -> FAILED ({} ms), errorType={}, errorMessage={}, requestId={}",
                    statement.getSqlCommandType(), statement.getId(), elapsedMillis(startedAt),
                    cause.getClass().getSimpleName(), cause.getMessage(), requestId(), exception);
            throw exception;
        }
    }

    private long affectedRows(Object result) {
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Number number) {
            return number.longValue();
        }
        return result == null ? 0 : 1;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return RequestIdFilter.get(request);
        }
        return "req_background";
    }

    private Throwable rootCause(Throwable exception) {
        Throwable result = exception;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }
}
