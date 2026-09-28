/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.SystemConfig;
import com.carolcoral.apiserver.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SystemConfigService 单元测试：读取、默认值回退、保存/更新与删除分支。
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    SystemConfigRepository repository;

    @InjectMocks
    SystemConfigService service;

    private SystemConfig config(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("存在配置时返回配置值")
        void returnsValue() {
            when(repository.findByConfigKey("k")).thenReturn(Optional.of(config("k", "v")));
            assertEquals("v", service.getConfig("k"));
        }

        @Test
        @DisplayName("不存在配置时返回 null")
        void returnsNull() {
            when(repository.findByConfigKey("k")).thenReturn(Optional.empty());
            assertNull(service.getConfig("k"));
        }
    }

    @Nested
    @DisplayName("getDefaultLanguage")
    class DefaultLanguage {

        @Test
        @DisplayName("已配置时返回配置值")
        void configured() {
            when(repository.findByConfigKey("defaultLanguage"))
                    .thenReturn(Optional.of(config("defaultLanguage", "en-US")));
            assertEquals("en-US", service.getDefaultLanguage());
        }

        @Test
        @DisplayName("未配置时回退 zh-CN")
        void fallback() {
            when(repository.findByConfigKey("defaultLanguage")).thenReturn(Optional.empty());
            assertEquals("zh-CN", service.getDefaultLanguage());
        }
    }

    @Nested
    @DisplayName("saveConfig")
    class SaveConfig {

        @Test
        @DisplayName("已存在则更新字段并保留原对象")
        void updateExisting() {
            SystemConfig existing = config("k", "old");
            when(repository.findByConfigKey("k")).thenReturn(Optional.of(existing));

            service.saveConfig("k", "new", "desc");

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(repository).save(captor.capture());
            SystemConfig saved = captor.getValue();
            assertSame(existing, saved);
            assertEquals("new", saved.getConfigValue());
            assertEquals("desc", saved.getDescription());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("不存在则新建并写入时间戳")
        void createNew() {
            when(repository.findByConfigKey("k")).thenReturn(Optional.empty());

            service.saveConfig("k", "v", "d");

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(repository).save(captor.capture());
            SystemConfig saved = captor.getValue();
            assertEquals("k", saved.getConfigKey());
            assertEquals("v", saved.getConfigValue());
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("deleteConfig")
    class DeleteConfig {

        @Test
        @DisplayName("存在则删除")
        void deletes() {
            SystemConfig existing = config("k", "v");
            when(repository.findByConfigKey("k")).thenReturn(Optional.of(existing));

            service.deleteConfig("k");

            verify(repository).delete(existing);
        }

        @Test
        @DisplayName("不存在则不执行删除")
        void noop() {
            when(repository.findByConfigKey("k")).thenReturn(Optional.empty());

            service.deleteConfig("k");

            verify(repository, never()).delete(any(SystemConfig.class));
        }
    }
}
