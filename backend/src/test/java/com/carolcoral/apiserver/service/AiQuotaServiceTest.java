/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiQuota;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiQuotaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiQuotaService 单元测试：额度校验、token 扣减、窗口重置与逐条扣减分支。
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {

    @Mock
    AiQuotaRepository repository;

    @InjectMocks
    AiQuotaService service;

    private AiQuota quota(long limit, long used, LocalDateTime windowStart, Integer windowSeconds) {
        AiQuota q = new AiQuota();
        q.setId(1L);
        q.setUser(new User());
        q.setTokenLimit(limit);
        q.setTokenUsed(used);
        q.setWindowStart(windowStart);
        q.setTimeWindowSeconds(windowSeconds);
        return q;
    }

    @Nested
    @DisplayName("hasQuota")
    class HasQuota {

        @Test
        @DisplayName("未配置额度时默认允许")
        void noQuotaConfigured() {
            when(repository.findByUserIdAndStatusTrue(1L)).thenReturn(Collections.emptyList());
            assertTrue(service.hasQuota(1L));
        }

        @Test
        @DisplayName("存在剩余额度时允许")
        void hasRemaining() {
            when(repository.findByUserIdAndStatusTrue(1L))
                    .thenReturn(List.of(quota(100, 10, LocalDateTime.now(), 3600)));
            assertTrue(service.hasQuota(1L));
        }

        @Test
        @DisplayName("额度已用尽且窗口未过期时拒绝")
        void exhausted() {
            when(repository.findByUserIdAndStatusTrue(1L))
                    .thenReturn(List.of(quota(100, 100, LocalDateTime.now(), 3600)));
            assertFalse(service.hasQuota(1L));
        }

        @Test
        @DisplayName("额度已用尽但窗口已过期时允许")
        void windowExpired() {
            when(repository.findByUserIdAndStatusTrue(1L))
                    .thenReturn(List.of(quota(100, 100, LocalDateTime.now().minusHours(10), 3600)));
            assertTrue(service.hasQuota(1L));
        }

        @Test
        @DisplayName("limit 为 0 或负数时视为无效")
        void invalidLimit() {
            when(repository.findByUserIdAndStatusTrue(1L))
                    .thenReturn(List.of(quota(0, 0, LocalDateTime.now(), 3600)));
            assertFalse(service.hasQuota(1L));
        }

        @Test
        @DisplayName("多条额度中任意一条有效即允许")
        void anyValid() {
            when(repository.findByUserIdAndStatusTrue(1L))
                    .thenReturn(Arrays.asList(
                            quota(10, 10, LocalDateTime.now(), 3600),
                            quota(100, 1, LocalDateTime.now(), 3600)));
            assertTrue(service.hasQuota(1L));
        }
    }

    @Nested
    @DisplayName("deductTokens")
    class DeductTokens {

        @Test
        @DisplayName("单条额度足够时全额扣减")
        void singleQuota() {
            AiQuota q = quota(100, 0, LocalDateTime.now(), 3600);
            when(repository.findByUserIdAndStatusTrue(1L)).thenReturn(List.of(q));

            service.deductTokens(1L, 30);

            assertEquals(30L, q.getTokenUsed());
            verify(repository).save(q);
        }

        @Test
        @DisplayName("跨多条额度累计扣减，直到扣完所需 token")
        void acrossQuotas() {
            AiQuota q1 = quota(50, 40, LocalDateTime.now(), 3600);
            AiQuota q2 = quota(50, 0, LocalDateTime.now(), 3600);
            when(repository.findByUserIdAndStatusTrue(1L)).thenReturn(Arrays.asList(q1, q2));

            service.deductTokens(1L, 15);

            assertEquals(50L, q1.getTokenUsed());
            assertEquals(5L, q2.getTokenUsed());
            verify(repository, times(2)).save(any(AiQuota.class));
        }

        @Test
        @DisplayName("跳过额度已用尽的条目")
        void skipExhausted() {
            AiQuota full = quota(10, 10, LocalDateTime.now(), 3600);
            AiQuota free = quota(20, 0, LocalDateTime.now(), 3600);
            when(repository.findByUserIdAndStatusTrue(1L)).thenReturn(Arrays.asList(full, free));

            service.deductTokens(1L, 5);

            assertEquals(10L, full.getTokenUsed());
            assertEquals(5L, free.getTokenUsed());
        }

        @Test
        @DisplayName("无可用额度时不报错")
        void noQuota() {
            when(repository.findByUserIdAndStatusTrue(1L)).thenReturn(Collections.emptyList());
            assertDoesNotThrow(() -> service.deductTokens(1L, 10));
            verify(repository, never()).save(any(AiQuota.class));
        }
    }

    @Nested
    @DisplayName("resetExpiredWindows")
    class ResetWindows {

        @Test
        @DisplayName("窗口已过期的条目被重置并保存")
        void resetsExpired() {
            AiQuota expired = quota(100, 80, LocalDateTime.now().minusHours(5), 3600);
            when(repository.findAll()).thenReturn(List.of(expired));

            service.resetExpiredWindows();

            assertEquals(0L, expired.getTokenUsed());
            assertTrue(expired.getWindowStart().isAfter(LocalDateTime.now().minusMinutes(1)));
            verify(repository).save(expired);
        }

        @Test
        @DisplayName("窗口未过期的条目保持不变")
        void keepsActive() {
            AiQuota active = quota(100, 80, LocalDateTime.now(), 3600);
            when(repository.findAll()).thenReturn(List.of(active));

            service.resetExpiredWindows();

            assertEquals(80L, active.getTokenUsed());
            verify(repository, never()).save(any(AiQuota.class));
        }

        @Test
        @DisplayName("windowStart 为空时跳过")
        void skipsNullWindow() {
            AiQuota q = quota(100, 80, null, 3600);
            when(repository.findAll()).thenReturn(List.of(q));

            service.resetExpiredWindows();

            verify(repository, never()).save(any(AiQuota.class));
        }
    }
}
