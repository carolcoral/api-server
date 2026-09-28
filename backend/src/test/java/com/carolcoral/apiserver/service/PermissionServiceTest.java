/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.Permission;
import com.carolcoral.apiserver.entity.RolePermission;
import com.carolcoral.apiserver.repository.PermissionRepository;
import com.carolcoral.apiserver.repository.RolePermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PermissionService 单元测试：分组排序、角色权限映射、CRUD 与关联清理。
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    PermissionRepository permissionRepository;

    @Mock
    RolePermissionRepository rolePermissionRepository;

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    PermissionService service;

    private Permission perm(Long id, String code, String group) {
        Permission p = new Permission();
        p.setId(id);
        p.setCode(code);
        p.setName(code);
        p.setGroupName(group);
        p.setType("PAGE");
        p.setSortOrder(0);
        return p;
    }

    private RolePermission rp(Long roleId, Long permissionId) {
        RolePermission r = new RolePermission();
        r.setRoleId(roleId);
        r.setPermissionId(permissionId);
        return r;
    }

    @Nested
    @DisplayName("getAllPermissionsGrouped")
    class Grouped {

        @Test
        @DisplayName("按预置菜单顺序分组排序，未登记分组排在已登记组之后")
        void sortOrder() {
            when(permissionRepository.findAllByOrderByIdDesc()).thenReturn(List.of(
                    perm(3L, "c", "用户管理"),
                    perm(2L, "b", "仪表盘管理"),
                    perm(1L, "a", "未知分组")));

            ApiResponse<List<Map<String, Object>>> res = service.getAllPermissionsGrouped();

            assertEquals(200, res.getCode());
            assertEquals(3, res.getData().size());
            assertEquals("仪表盘管理", res.getData().get(0).get("groupName"));
            assertEquals("用户管理", res.getData().get(1).get("groupName"));
            assertEquals("未知分组", res.getData().get(2).get("groupName"));
        }

        @Test
        @DisplayName("仓库异常兜底")
        void error() {
            when(permissionRepository.findAllByOrderByIdDesc()).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getAllPermissionsGrouped().getCode());
        }
    }

    @Nested
    @DisplayName("getRolePermissionIds")
    class RolePerms {

        @Test
        @DisplayName("返回权限 ID 列表")
        void lists() {
            when(rolePermissionRepository.findByRoleId(1L))
                    .thenReturn(List.of(rp(1L, 10L), rp(1L, 20L)));

            assertEquals(List.of(10L, 20L), service.getRolePermissionIds(1L).getData());
        }

        @Test
        @DisplayName("异常兜底")
        void error() {
            when(rolePermissionRepository.findByRoleId(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getRolePermissionIds(1L).getCode());
        }
    }

    @Nested
    @DisplayName("assignPermissions")
    class Assign {

        @Test
        @DisplayName("先清理旧关联再逐条插入")
        void replacesAndInserts() {
            ApiResponse<Void> res = service.assignPermissions(1L, List.of(10L, 20L));

            assertEquals(200, res.getCode());
            verify(rolePermissionRepository).deleteByRoleId(1L);
            verify(rolePermissionRepository).flush();
            verify(jdbcTemplate, times(2)).update(startsWith("INSERT OR IGNORE"), eq(1L), any(Object.class));
        }

        @Test
        @DisplayName("空列表时只清理不插入")
        void emptyList() {
            assertEquals(200, service.assignPermissions(1L, List.of()).getCode());
            verify(jdbcTemplate, never()).update(anyString(), eq(1L), any(Object.class));
        }

        @Test
        @DisplayName("null 列表同样安全")
        void nullList() {
            assertEquals(200, service.assignPermissions(1L, null).getCode());
        }
    }

    @Nested
    @DisplayName("createPermission")
    class Create {

        @Test
        @DisplayName("编码重复被拒绝")
        void duplicate() {
            when(permissionRepository.existsByCode("A")).thenReturn(true);
            assertNotEquals(200, service.createPermission(perm(null, "A", "g")).getCode());
            verify(permissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("创建成功")
        void success() {
            when(permissionRepository.existsByCode("A")).thenReturn(false);
            when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            assertEquals(200, service.createPermission(perm(null, "A", "g")).getCode());
        }
    }

    @Nested
    @DisplayName("updatePermission")
    class Update {

        @Test
        @DisplayName("权限不存在")
        void missing() {
            when(permissionRepository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updatePermission(1L, perm(1L, "A", "g")).getCode());
        }

        @Test
        @DisplayName("改编码且新编码冲突")
        void codeConflict() {
            when(permissionRepository.findById(1L)).thenReturn(Optional.of(perm(1L, "A", "g")));
            when(permissionRepository.existsByCode("B")).thenReturn(true);
            assertNotEquals(200, service.updatePermission(1L, perm(1L, "B", "g")).getCode());
        }

        @Test
        @DisplayName("更新成功并同步非空字段")
        void success() {
            Permission existing = perm(1L, "A", "g");
            when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Permission req = perm(1L, "A", "g2");
            req.setSortOrder(9);
            assertEquals(200, service.updatePermission(1L, req).getCode());
            assertEquals("g2", existing.getGroupName());
            assertEquals(9, existing.getSortOrder());
        }

        @Test
        @DisplayName("sortOrder 为空时保留原值")
        void keepSortOrder() {
            Permission existing = perm(1L, "A", "g");
            existing.setSortOrder(3);
            when(permissionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(permissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Permission req = perm(1L, "A", "g");
            req.setSortOrder(null);
            service.updatePermission(1L, req);

            assertEquals(3, existing.getSortOrder());
        }
    }

    @Nested
    @DisplayName("deletePermission")
    class Delete {

        @Test
        @DisplayName("权限不存在")
        void missing() {
            when(permissionRepository.existsById(1L)).thenReturn(false);
            assertNotEquals(200, service.deletePermission(1L).getCode());
        }

        @Test
        @DisplayName("删除成功并清理关联")
        void success() {
            when(permissionRepository.existsById(1L)).thenReturn(true);
            assertEquals(200, service.deletePermission(1L).getCode());
            verify(rolePermissionRepository).deleteByPermissionId(1L);
            verify(permissionRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("权限编码集合")
    class Codes {

        @Test
        @DisplayName("getAllPermissionCodes 收集全部编码")
        void all() {
            when(permissionRepository.findAll())
                    .thenReturn(List.of(perm(1L, "A", "g"), perm(2L, "B", "g")));
            assertEquals(Set.of("A", "B"), service.getAllPermissionCodes());
        }

        @Test
        @DisplayName("空角色列表返回空集合")
        void emptyRoles() {
            assertTrue(service.getUserPermissionCodes(Collections.emptyList()).isEmpty());
            assertTrue(service.getUserPermissionCodes(null).isEmpty());
        }

        @Test
        @DisplayName("按角色聚合权限编码")
        void byRoles() {
            when(rolePermissionRepository.findByRoleIdIn(List.of(1L)))
                    .thenReturn(List.of(rp(1L, 10L), rp(1L, 20L)));
            when(permissionRepository.findAllById(anySet()))
                    .thenReturn(List.of(perm(10L, "A", "g"), perm(20L, "B", "g")));

            assertEquals(Set.of("A", "B"), service.getUserPermissionCodes(List.of(1L)));
        }

        @Test
        @DisplayName("无关联权限时返回空集合")
        void noPerms() {
            when(rolePermissionRepository.findByRoleIdIn(List.of(1L))).thenReturn(Collections.emptyList());
            assertTrue(service.getUserPermissionCodes(List.of(1L)).isEmpty());
        }

        @Test
        @DisplayName("异常兜底返回空集合")
        void error() {
            when(rolePermissionRepository.findByRoleIdIn(List.of(1L))).thenThrow(new RuntimeException("boom"));
            assertTrue(service.getUserPermissionCodes(List.of(1L)).isEmpty());
        }
    }
}
