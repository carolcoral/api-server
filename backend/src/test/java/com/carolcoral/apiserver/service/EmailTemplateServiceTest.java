/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.EmailTemplate;
import com.carolcoral.apiserver.repository.EmailTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmailTemplateService 单元测试：类型校验、同类型唯一启用互斥与 CRUD 分支。
 */
@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock
    EmailTemplateRepository repository;

    @InjectMocks
    EmailTemplateService service;

    private EmailTemplate template(Long id, String type, boolean enabled) {
        EmailTemplate t = new EmailTemplate();
        t.setId(id);
        t.setName("t" + id);
        t.setType(type);
        t.setEnabled(enabled);
        return t;
    }

    @Nested
    @DisplayName("查询")
    class Query {

        @Test
        @DisplayName("getAllTemplates 成功与异常兜底")
        void all() {
            when(repository.findAll()).thenReturn(List.of(template(1L, EmailTemplate.TYPE_REGISTER, true)));
            assertEquals(200, service.getAllTemplates().getCode());

            when(repository.findAll()).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getAllTemplates().getCode());
        }

        @Test
        @DisplayName("getTemplateById 命中与未命中")
        void byId() {
            when(repository.findById(1L)).thenReturn(Optional.of(template(1L, EmailTemplate.TYPE_REGISTER, true)));
            assertEquals(200, service.getTemplateById(1L).getCode());

            when(repository.findById(2L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.getTemplateById(2L).getCode());
        }

        @Test
        @DisplayName("getTemplateById 异常兜底")
        void byIdError() {
            when(repository.findById(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getTemplateById(1L).getCode());
        }
    }

    @Nested
    @DisplayName("createTemplate")
    class Create {

        @Test
        @DisplayName("不支持的模板类型被拒绝")
        void badType() {
            EmailTemplate t = template(null, "UNKNOWN", true);
            assertNotEquals(200, service.createTemplate(t).getCode());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("启用时自动禁用同类型其他启用模板")
        void disablesSiblings() {
            EmailTemplate existing = template(9L, EmailTemplate.TYPE_REGISTER, true);
            when(repository.findByType(EmailTemplate.TYPE_REGISTER)).thenReturn(List.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.createTemplate(template(null, EmailTemplate.TYPE_REGISTER, true)).getCode());

            assertFalse(existing.getEnabled());
            verify(repository).save(existing);
        }

        @Test
        @DisplayName("创建成功（未启用）")
        void success() {
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            assertEquals(200, service.createTemplate(template(null, EmailTemplate.TYPE_REGISTER, false)).getCode());
        }
    }

    @Nested
    @DisplayName("updateTemplate")
    class Update {

        @Test
        @DisplayName("模板不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updateTemplate(1L, template(1L, EmailTemplate.TYPE_REGISTER, true)).getCode());
        }

        @Test
        @DisplayName("类型非法时拒绝")
        void badType() {
            when(repository.findById(1L)).thenReturn(Optional.of(template(1L, EmailTemplate.TYPE_REGISTER, true)));
            assertNotEquals(200, service.updateTemplate(1L, template(1L, "BAD", true)).getCode());
        }

        @Test
        @DisplayName("更新成功并同步非空字段")
        void success() {
            EmailTemplate existing = template(1L, EmailTemplate.TYPE_REGISTER, false);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            EmailTemplate req = template(1L, EmailTemplate.TYPE_REGISTER, true);
            req.setSubject("s");
            req.setContent("c");
            assertEquals(200, service.updateTemplate(1L, req).getCode());
            assertEquals("s", existing.getSubject());
            assertEquals("c", existing.getContent());
            assertTrue(existing.getEnabled());
        }

        @Test
        @DisplayName("启用状态变化时禁用同类型其他模板")
        void disablesOnEnableChange() {
            EmailTemplate existing = template(1L, EmailTemplate.TYPE_REGISTER, false);
            EmailTemplate sibling = template(2L, EmailTemplate.TYPE_REGISTER, true);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.findByType(EmailTemplate.TYPE_REGISTER)).thenReturn(List.of(existing, sibling));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.updateTemplate(1L, template(1L, EmailTemplate.TYPE_REGISTER, true));

            assertFalse(sibling.getEnabled());
        }

        @Test
        @DisplayName("更新异常兜底")
        void error() {
            when(repository.findById(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.updateTemplate(1L, template(1L, EmailTemplate.TYPE_REGISTER, true)).getCode());
        }
    }

    @Nested
    @DisplayName("deleteTemplate")
    class Delete {

        @Test
        @DisplayName("不存在")
        void missing() {
            when(repository.existsById(1L)).thenReturn(false);
            assertNotEquals(200, service.deleteTemplate(1L).getCode());
        }

        @Test
        @DisplayName("删除成功")
        void success() {
            when(repository.existsById(1L)).thenReturn(true);
            assertEquals(200, service.deleteTemplate(1L).getCode());
            verify(repository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("searchTemplates")
    class Search {

        @Test
        @DisplayName("异常兜底")
        void error() {
            when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                    any(org.springframework.data.domain.PageRequest.class)))
                    .thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.searchTemplates("n", null, null, 0, 10).getCode());
        }
    }
}
