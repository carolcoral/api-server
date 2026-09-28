/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ChatCompletionResponse;
import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.AiProvider;
import com.carolcoral.apiserver.entity.AiUsageLog;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiUsageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiUsageService 单元测试：成功/失败/流式日志落库、费用计算与截断分支。
 */
@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock
    AiUsageLogRepository repository;

    AiUsageService service;

    @BeforeEach
    void setUp() {
        service = new AiUsageService(repository, new ObjectMapper());
    }

    private AiModel model(Double in, Double out) {
        AiModel m = new AiModel();
        m.setId(1L);
        m.setInputPrice(in);
        m.setOutputPrice(out);
        return m;
    }

    private ChatCompletionResponse response(Integer prompt, Integer completion) {
        ChatCompletionResponse r = new ChatCompletionResponse();
        if (prompt != null) {
            ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
            usage.setPromptTokens(prompt);
            usage.setCompletionTokens(completion);
            usage.setTotalTokens(prompt + completion);
            r.setUsage(usage);
        }
        return r;
    }

    @Nested
    @DisplayName("logSuccess")
    class LogSuccess {

        @Test
        @DisplayName("按单价计算费用并入库")
        void computesCost() {
            service.logSuccess(new User(), new AiProvider(), model(1.0, 2.0),
                    "req", response(1000, 500), 12L, null);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            AiUsageLog saved = captor.getValue();
            assertEquals(200, saved.getStatusCode());
            // 1.0 * 1000 / 1000 + 2.0 * 500 / 1000 = 2.0
            assertEquals(2.0, saved.getCost(), 1e-9);
            assertEquals(1000, saved.getPromptTokens());
            assertEquals(500, saved.getCompletionTokens());
            assertEquals(1500, saved.getTotalTokens());
        }

        @Test
        @DisplayName("usage 为空时不计 token 与费用")
        void noUsage() {
            service.logSuccess(new User(), new AiProvider(), model(1.0, 2.0),
                    "req", response(null, null), 5L, null);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().getCost());
        }

        @Test
        @DisplayName("单价为空时费用按 0 处理")
        void nullPrices() {
            service.logSuccess(new User(), new AiProvider(), model(null, null),
                    "req", response(10, 10), 1L, 7L);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            assertEquals(0.0, captor.getValue().getCost(), 1e-9);
            assertEquals(7L, captor.getValue().getFallbackFrom());
        }

        @Test
        @DisplayName("入库异常被吞掉，不影响调用方")
        void swallowsException() {
            when(repository.save(any())).thenThrow(new RuntimeException("db down"));
            assertDoesNotThrow(() -> service.logSuccess(new User(), new AiProvider(),
                    model(1.0, 1.0), "req", response(1, 1), 1L, null));
        }
    }

    @Nested
    @DisplayName("logStreamSuccess")
    class LogStream {

        @Test
        @DisplayName("写入 token 与费用")
        void writes() {
            service.logStreamSuccess(new User(), new AiProvider(), model(1.0, 1.0),
                    "req", 10, 20, 30, 99L, 0.5);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            AiUsageLog saved = captor.getValue();
            assertEquals(10, saved.getPromptTokens());
            assertEquals(20, saved.getCompletionTokens());
            assertEquals(30, saved.getTotalTokens());
            assertEquals(0.5, saved.getCost(), 1e-9);
        }
    }

    @Nested
    @DisplayName("logFailure")
    class LogFailure {

        @Test
        @DisplayName("记录错误状态与信息")
        void writes() {
            service.logFailure(new User(), new AiProvider(), model(1.0, 1.0),
                    "req", 500, "boom", 3L);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            AiUsageLog saved = captor.getValue();
            assertEquals(500, saved.getStatusCode());
            assertEquals("boom", saved.getErrorMsg());
            assertEquals(0.0, saved.getCost(), 1e-9);
        }
    }

    @Nested
    @DisplayName("请求体截断")
    class Truncate {

        @Test
        @DisplayName("超长请求体被截断并追加标记")
        void truncated() {
            String longBody = "x".repeat(6000);
            service.logFailure(new User(), new AiProvider(), model(null, null),
                    longBody, 400, "e", 1L);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            String body = captor.getValue().getRequestBody();
            assertTrue(body.length() < 6000);
            assertTrue(body.endsWith("...[truncated]"));
        }

        @Test
        @DisplayName("空请求体保持 null")
        void nullBody() {
            service.logFailure(new User(), new AiProvider(), model(null, null),
                    null, 400, "e", 1L);

            ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().getRequestBody());
        }
    }
}
