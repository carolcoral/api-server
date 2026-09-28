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
 * AiModelSelector 单元测试：可用性判定、熔断冷却、失败/成功标记与 fallback 候选。
 */
@ExtendWith(MockitoExtension.class)
class AiModelSelectorTest {

    @Mock
    AiModelRepository modelRepository;

    @InjectMocks
    AiModelSelector selector;

    private AiModel model(Long id, String name, boolean status) {
        AiModel m = new AiModel();
        m.setId(id);
        m.setModelName(name);
        m.setStatus(status);
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
            assertFalse(selector.isModelAvailable(model(1L, "m", false)));
        }

        @Test
        @DisplayName("冷却期内不可用")
        void cooling() {
            AiModel m = model(1L, "m", true);
            m.setCooldownUntil(LocalDateTime.now().plusMinutes(5));
            assertFalse(selector.isModelAvailable(m));
        }

        @Test
        @DisplayName("冷却已过期即可用")
        void ready() {
            AiModel m = model(1L, "m", true);
            m.setCooldownUntil(LocalDateTime.now().minusMinutes(1));
            assertTrue(selector.isModelAvailable(m));
        }
    }

    @Nested
    @DisplayName("selectModels")
    class Select {

        @Test
        @DisplayName("priority 按模型名稳定排序并返回全部可用模型")
        void priorityKeepsAll() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(3L, "c", true), model(1L, "a", true), model(2L, "b", false)));

            List<AiModel> result = selector.selectModels("priority");

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(m -> m.getStatus()));
        }

        @Test
        @DisplayName("random 策略返回全部可用模型")
        void random() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "a", true), model(2L, "b", true)));

            assertEquals(2, selector.selectModels("random").size());
        }

        @Test
        @DisplayName("未知策略回退为默认排序，不报错")
        void unknownStrategy() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "a", true)));
            assertEquals(1, selector.selectModels("whatever").size());
        }

        @Test
        @DisplayName("无可用模型返回空列表")
        void empty() {
            when(modelRepository.findByStatusTrueWithProvider()).thenReturn(List.of());

            assertTrue(selector.selectModels(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("findModel / fallback 候选")
    class Lookup {

        @Test
        @DisplayName("按模型名命中启用模型")
        void found() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "gpt", true)));

            assertTrue(selector.findModel("gpt").isPresent());
            assertFalse(selector.findModel("missing").isPresent());
        }

        @Test
        @DisplayName("fallback 候选排除已尝试模型并限制上限")
        void excludesTried() {
            when(modelRepository.findByStatusTrueWithProvider())
                    .thenReturn(List.of(model(1L, "a", true), model(2L, "b", true), model(3L, "c", true)));

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
    @DisplayName("失败 / 成功标记（熔断）")
    class Marking {

        @Test
        @DisplayName("失败累加计数，未达阈值不进入冷却")
        void failure() {
            AiModel m = model(1L, "m", true);
            m.setConsecutiveFailures(0);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelFailureAsync(1L);

            assertEquals(1, m.getConsecutiveFailures());
            assertNull(m.getCooldownUntil());
            verify(modelRepository).save(m);
        }

        @Test
        @DisplayName("连续失败达到阈值进入冷却期")
        void offlineThreshold() {
            AiModel m = model(1L, "m", true);
            m.setConsecutiveFailures(4);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelFailureAsync(1L);

            assertEquals(5, m.getConsecutiveFailures());
            assertNotNull(m.getCooldownUntil());
            assertTrue(m.getCooldownUntil().isAfter(LocalDateTime.now()));
        }

        @Test
        @DisplayName("失败计数为 null 时不抛异常")
        void nullFailures() {
            AiModel m = model(1L, "m", true);
            m.setConsecutiveFailures(null);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            assertDoesNotThrow(() -> selector.markModelFailureAsync(1L));
            assertEquals(1, m.getConsecutiveFailures());
        }

        @Test
        @DisplayName("成功重置失败计数并清除冷却")
        void success() {
            AiModel m = model(1L, "m", true);
            m.setConsecutiveFailures(3);
            m.setCooldownUntil(LocalDateTime.now().plusMinutes(5));
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelSuccessAsync(1L, 100L);

            assertEquals(0, m.getConsecutiveFailures());
            assertNull(m.getCooldownUntil());
        }

        @Test
        @DisplayName("同步包装方法转发到异步实现")
        void syncWrappers() {
            AiModel m = model(1L, "m", true);
            when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

            selector.markModelFailure(m);
            selector.markModelSuccess(m, 10L);

            verify(modelRepository, times(2)).save(any());
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
                    model(1L, "a", true),
                    model(2L, "b", false)));

            assertEquals(1, selector.getAllEnabledModels().size());
        }

        @Test
        @DisplayName("忽略可用性直接返回（用于内部路由）")
        void ignoreHealth() {
            AiModel cooling = model(1L, "a", false);
            cooling.setCooldownUntil(LocalDateTime.now().plusMinutes(5));
            when(modelRepository.findByStatusTrueWithProvider()).thenReturn(List.of(cooling));
            assertEquals(1, selector.getAllEnabledModelsIgnoreHealth().size());
        }
    }
}
