/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.repository.RoleRepository;
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
 * RoleService 单元测试：CRUD、唯一性校验、默认角色互斥与异常兜底。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    RoleRepository repository;

    @InjectMocks
    RoleService service;

    private Role role(Long id, String name, String code) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        r.setCode(code);
        r.setIsDefault(false);
        return r;
    }

    @Nested
    @DisplayName("查询")
    class Query {

        @Test
        @DisplayName("getAllRoles 成功")
        void all() {
            when(repository.findAll()).thenReturn(List.of(role(1L, "a", "A")));
            ApiResponse<List<Role>> res = service.getAllRoles();
            assertEquals(200, res.getCode());
            assertEquals(1, res.getData().size());
        }

        @Test
        @DisplayName("getAllRoles 异常兜底")
        void allError() {
            when(repository.findAll()).thenThrow(new RuntimeException("boom"));
            assertEquals(500, service.getAllRoles().getCode());
        }

        @Test
        @DisplayName("getRoleById 命中")
        void byId() {
            when(repository.findById(1L)).thenReturn(Optional.of(role(1L, "a", "A")));
            assertEquals(200, service.getRoleById(1L).getCode());
        }

        @Test
        @DisplayName("getRoleById 不存在")
        void byIdMissing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.getRoleById(1L).getCode());
        }

        @Test
        @DisplayName("getDefaultRole 命中与未设置")
        void defaultRole() {
            when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(role(1L, "a", "A")));
            assertEquals(200, service.getDefaultRole().getCode());
        }

        @Test
        @DisplayName("getDefaultRole 未设置")
        void defaultRoleMissing() {
            when(repository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            assertNotEquals(200, service.getDefaultRole().getCode());
        }
    }

    @Nested
    @DisplayName("创建")
    class Create {

        @Test
        @DisplayName("编码重复被拒绝")
        void duplicateCode() {
            when(repository.existsByCode("A")).thenReturn(true);
            assertNotEquals(200, service.createRole(role(null, "a", "A")).getCode());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("名称重复被拒绝")
        void duplicateName() {
            when(repository.existsByCode("A")).thenReturn(false);
            when(repository.existsByName("a")).thenReturn(true);
            assertNotEquals(200, service.createRole(role(null, "a", "A")).getCode());
        }

        @Test
        @DisplayName("创建成功")
        void success() {
            when(repository.existsByCode("A")).thenReturn(false);
            when(repository.existsByName("a")).thenReturn(false);
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            assertEquals(200, service.createRole(role(null, "a", "A")).getCode());
        }

        @Test
        @DisplayName("默认角色创建时清除既有默认")
        void clearsDefault() {
            Role d = role(null, "a", "A");
            d.setIsDefault(true);
            when(repository.existsByCode("A")).thenReturn(false);
            when(repository.existsByName("a")).thenReturn(false);
            Role old = role(9L, "old", "OLD");
            old.setIsDefault(true);
            when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(old));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.createRole(d);

            verify(repository).save(old);
            assertFalse(old.getIsDefault());
        }

        @Test
        @DisplayName("保存异常兜底")
        void saveError() {
            when(repository.existsByCode("A")).thenReturn(false);
            when(repository.existsByName("a")).thenReturn(false);
            when(repository.save(any())).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.createRole(role(null, "a", "A")).getCode());
        }
    }

    @Nested
    @DisplayName("更新")
    class Update {

        @Test
        @DisplayName("角色不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updateRole(1L, role(1L, "a", "A")).getCode());
        }

        @Test
        @DisplayName("改编码且新编码重复")
        void codeConflict() {
            when(repository.findById(1L)).thenReturn(Optional.of(role(1L, "a", "A")));
            when(repository.existsByCode("B")).thenReturn(true);
            assertNotEquals(200, service.updateRole(1L, role(1L, "a", "B")).getCode());
        }

        @Test
        @DisplayName("改名且新名称重复")
        void nameConflict() {
            Role existing = role(1L, "a", "A");
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.existsByName("b")).thenReturn(true);
            assertNotEquals(200, service.updateRole(1L, role(1L, "b", "A")).getCode());
        }

        @Test
        @DisplayName("更新成功并同步字段")
        void success() {
            Role existing = role(1L, "a", "A");
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.existsByCode("A2")).thenReturn(false);
            when(repository.existsByName("a2")).thenReturn(false);
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            Role req = role(1L, "a2", "A2");
            req.setDescription("d");
            assertEquals(200, service.updateRole(1L, req).getCode());
            assertEquals("a2", existing.getName());
            assertEquals("A2", existing.getCode());
        }

        @Test
        @DisplayName("提升为默认角色时清除旧默认")
        void promoteDefault() {
            Role existing = role(1L, "a", "A");
            Role old = role(9L, "old", "OLD");
            old.setIsDefault(true);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(old));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            Role req = role(1L, "a", "A");
            req.setIsDefault(true);
            service.updateRole(1L, req);

            assertFalse(old.getIsDefault());
            assertTrue(existing.getIsDefault());
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("不存在时拒绝")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.deleteRole(1L).getCode());
        }

        @Test
        @DisplayName("系统管理员角色不可删除")
        void adminProtected() {
            when(repository.findById(1L)).thenReturn(Optional.of(role(1L, "admin", "ROLE_ADMIN")));
            assertNotEquals(200, service.deleteRole(1L).getCode());
            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("删除成功")
        void success() {
            when(repository.findById(1L)).thenReturn(Optional.of(role(1L, "a", "A")));
            assertEquals(200, service.deleteRole(1L).getCode());
            verify(repository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("设置默认角色")
    class SetDefault {

        @Test
        @DisplayName("角色不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.setDefaultRole(1L).getCode());
        }

        @Test
        @DisplayName("设置成功并标记默认")
        void success() {
            Role r = role(1L, "a", "A");
            when(repository.findById(1L)).thenReturn(Optional.of(r));
            when(repository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.setDefaultRole(1L).getCode());
            assertTrue(r.getIsDefault());
        }
    }
}
