package com.xc.agent.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseOperationLoggingInterceptorTest {
    @Mock Executor executor;
    @Mock MappedStatement statement;
    @Mock ResultHandler<Object> resultHandler;

    @Test
    void preservesQueryResultWhileLoggingStatementMetadata() throws Throwable {
        Method query = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        RowBounds rowBounds = RowBounds.DEFAULT;
        List<Object> expected = List.of("one", "two");
        when(statement.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
        when(statement.getId()).thenReturn("com.xc.agent.mapper.FaqItemMapper.selectPublishedPage");
        when(executor.query(any(), any(), any(), any())).thenReturn(expected);
        Invocation invocation = new Invocation(executor, query,
                new Object[]{statement, new Object(), rowBounds, resultHandler});

        Object result = new DatabaseOperationLoggingInterceptor().intercept(invocation);

        assertThat(result).isSameAs(expected);
    }
}
