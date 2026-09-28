/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.AiModel;
import com.carolcoral.apiserver.entity.AiProvider;
import com.carolcoral.apiserver.repository.AiModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiModelSelector 单元测试：可用性判定、策略排序、auto 模式与失败/成功标记。
 */
@ExtendWith(MockitoExtension.class)
class AiModelSelectorTest {

    @Mock
    AiModelRepository modelRepository;

    @InjectMocks
    AiModelSelector selector;

    private AiModel model(Long id, String name, boolean status, String health) {
        AiModel m = new AiModel();
        m.setId(id);
        m.setModelName(name);
        m.setStatus(status);
        m.setHealthStatus(health);
        m.setProvider(new AiProvider());
        return m;
    }

    @Nested
    @DisplayName("isModelAvailable")
    class Available {

        @Test
        @DisplayName("null 或未启用不可用")
        void nullOrDisabled() {
            assertFalse(selector.isModelAvailable(null));
            assertFalse(selector.isModelAvailable(model(1L, "m", false, "online")));
        }

        @Test
        @DisplayName("offline 不可用")
        void offline() {
            assertFalse(selector.isModelAvailable(model(1L, "m", true, "offline")));
        }

        @Test
        @DisplayName("冷却期内不可用")
        void cooling() {
            AiModel m = model(1L, "m", true, "degraded");
            m.setCooldownUntil(LocalDateTime.now().plusMinutes(5));
            assertFalse(selector.isModelAvailable(m));
        }

        @Test
        @DisplayName("冷却已过期且健康可用")
        void ready() {
            AiModel m = model(1L, "m", true, "degraded");
            m.setCooldownUntil(LocalDateTime.now().minusMinutes(1));
            assertTrue(selector.isModelAvailable(m));
        }
    }

    @Nested
    @DisplayName("selectModels")
    class Select {

        @Test
        @DisplayName("cost_first 按输入单价升序，null 排最后")
        void costFirst() {
            AiModel cheap = model(1L, "cheap", true, "online");
            cheap.setInputPrice(0.1);
            AiModel pricey = model(2L, "pricey", true, "online");
            pricey.setInputPrice(10.0);
            AiModel noPrice = model(3L, "nop", true, "online");
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(noPrice, pricey, cheap));

            List<AiModel> result = selector.selectModels("cost_first");

            assertEquals(1L, result.get(0).getId());
            assertEquals(3L, result.get(2).getId());
        }

        @Test
        @DisplayName("performance_first 按平均延迟升序")
        void perfFirst() {
            AiModel fast = model(1L, "fast", true, "online");
            fast.setAvgLatencyMs(10L);
            AiModel slow = model(2L, "slow", true, "online");
            slow.setAvgLatencyMs(999L);
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(slow, fast));

            assertEquals(1L, selector.selectModels("performance_first").get(0).getId());
        }

        @Test
        @DisplayName("无可用模型返回空列表")
        void empty() {
            when(modelRepository.findByStatusTrueWithProvider()).thenReturn(List.of());

            assertTrue(selector.selectModels(null).isEmpty());
        }

        @Test
        @DisplayName("排除 autoMode 模型自身")
        void excludesAutoMode() {
            AiModel autoModel = model(99L, "auto", true, "online");
            autoModel.setAutoMode(true);
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(autoModel, model(2L, "real", true, "online")));

            List<AiModel> result = selector.selectModels("priority");

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).getId());
        }
    }

    @Nested
    @DisplayName("findModel / fallback 候选")
    class Lookup {

        @Test
        @DisplayName("按模型名命中启用模型")
        void found() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "gpt", true, "online")));

            assertTrue(selector.findModel("gpt").isPresent());
            assertFalse(selector.findModel("missing").isPresent());
        }

        @Test
        @DisplayName("fallback 候选排除已尝试模型并限制上限")
        void excludesTried() {
            AiModel m1 = model(1L, "a", true, "online");
            AiModel m2 = model(2L, "b", true, "online");
            AiModel m3 = model(3L, "c", true, "online");
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(m1, m2, m3));

            List<AiModel> result = selector.getFallbackCandidates("priority", Set.of(1L));

            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(m -> m.getId().equals(1L)));
        }

        @Test
        @DisplayName("getMaxFallbackRetries 返回上限 3")
        void retries() {
            assertEquals(3, selector.getMaxFallbackRetries());
        }
    }

    @Nested
    @DisplayName("失败 / 成功标记")
    class Marking {

        @Test
        @DisplayName("失败累加并进入 degraded")
        void failure() {
            AiModel m = model(1L, "m", true, "online");
            m.setConsecutiveFailures(0);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelFailureAsync(1L);

            assertEquals(1, m.getConsecutiveFailures());
            assertEquals("degraded", m.getHealthStatus());
            verify(modelRepository).save(m);
        }

        @Test
        @DisplayName("连续失败达到阈值转为 offline 并设置冷却")
        void offlineThreshold() {
            AiModel m = model(1L, "m", true, "degraded");
            m.setConsecutiveFailures(4);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelFailureAsync(1L);

            assertEquals("offline", m.getHealthStatus());
            assertNotNull(m.getCooldownUntil());
        }

        @Test
        @DisplayName("成功重置失败计数并首次写入延迟")
        void successFirst() {
            AiModel m = model(1L, "m", true, "degraded");
            m.setConsecutiveFailures(3);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelSuccessAsync(1L, 100L);

            assertEquals(0, m.getConsecutiveFailures());
            assertEquals("online", m.getHealthStatus());
            assertEquals(100L, m.getAvgLatencyMs());
            assertNull(m.getCooldownUntil());
        }

        @Test
        @DisplayName("已有延迟时按指数移动平均更新")
        void successEma() {
            AiModel m = model(1L, "m", true, "online");
            m.setAvgLatencyMs(100L);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelSuccessAsync(1L, 200L);

            assertEquals((100L * 9 + 200L) / 10, m.getAvgLatencyMs());
        }

        @Test
        @DisplayName("模型不存在时安全返回")
        void missingModel() {
            when(modelRepository.findById(1L)).thenReturn(Optional.empty());
            assertDoesNotThrow(() -> selector.markModelFailureAsync(1L));
            verify(modelRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("启用模型查询")
    class EnabledModels {

        @Test
        @DisplayName("过滤不可用模型")
        void filterUnavailable() {
            when(modelRepository.findByStatusTrueWithProvider()).thenReturn(List.of(
                    model(1L, "a", true, "online"),
                    model(2L, "b", false, "online")));

            assertEquals(1, selector.getAllEnabledModels().size());
        }

        @Test
        @DisplayName("忽略健康状态直接返回（用于内部路由）")
        void ignoreHealth() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "a", false, "offline")));
            assertEquals(1, selector.getAllEnabledModelsIgnoreHealth().size());
        }
    }
}
