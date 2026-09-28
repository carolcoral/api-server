/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ProjectMemberDTO;
import com.carolcoral.apiserver.entity.ProjectMember;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.ProjectMemberRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProjectMemberService 单元测试：增删改、去重校验、用户信息装配与异常兜底。
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock
    ProjectMemberRepository memberRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ProjectMemberService service;

    private ProjectMember member(Long id, Long projectId, Long userId, ProjectMember.MemberRole role) {
        ProjectMember m = new ProjectMember();
        m.setId(id);
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    private User user(Long id, String username, String email) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        return u;
    }

    @Nested
    @DisplayName("addProjectMember")
    class Add {

        @Test
        @DisplayName("已是成员时拒绝")
        void duplicate() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L))
                    .thenReturn(Optional.of(member(1L, 1L, 2L, ProjectMember.MemberRole.MEMBER)));

            assertNotEquals(200, service.addProjectMember(1L, 2L, ProjectMember.MemberRole.MEMBER).getCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("新增成功并返回字段")
        void success() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
            when(memberRepository.save(any())).thenAnswer(inv -> {
                ProjectMember m = inv.getArgument(0);
                m.setId(5L);
                return m;
            });

            ApiResponse<ProjectMember> res = service.addProjectMember(1L, 2L, ProjectMember.MemberRole.ADMIN);

            assertEquals(200, res.getCode());
            assertEquals(5L, res.getData().getId());
            assertEquals(ProjectMember.MemberRole.ADMIN, res.getData().getRole());
        }

        @Test
        @DisplayName("保存异常兜底")
        void error() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
            when(memberRepository.save(any())).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.addProjectMember(1L, 2L, ProjectMember.MemberRole.ADMIN).getCode());
        }
    }

    @Nested
    @DisplayName("updateMemberRole")
    class Update {

        @Test
        @DisplayName("成员不存在")
        void missing() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updateMemberRole(1L, 2L, ProjectMember.MemberRole.ADMIN).getCode());
        }

        @Test
        @DisplayName("更新成功")
        void success() {
            ProjectMember existing = member(5L, 1L, 2L, ProjectMember.MemberRole.MEMBER);
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));
            when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ApiResponse<ProjectMember> res = service.updateMemberRole(1L, 2L, ProjectMember.MemberRole.ADMIN);

            assertEquals(200, res.getCode());
            assertEquals(ProjectMember.MemberRole.ADMIN, res.getData().getRole());
        }
    }

    @Nested
    @DisplayName("removeProjectMember")
    class Remove {

        @Test
        @DisplayName("成员不存在")
        void missing() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.removeProjectMember(1L, 2L).getCode());
        }

        @Test
        @DisplayName("删除成功")
        void success() {
            ProjectMember existing = member(5L, 1L, 2L, ProjectMember.MemberRole.MEMBER);
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));

            assertEquals(200, service.removeProjectMember(1L, 2L).getCode());
            verify(memberRepository).delete(existing);
        }
    }

    @Nested
    @DisplayName("getProjectMembers")
    class ListMembers {

        @Test
        @DisplayName("空列表直接返回")
        void empty() {
            when(memberRepository.findByProjectId(1L)).thenReturn(Collections.emptyList());

            ApiResponse<List<ProjectMemberDTO>> res = service.getProjectMembers(1L);

            assertEquals(200, res.getCode());
            assertTrue(res.getData().isEmpty());
            verify(userRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("装配用户名与邮箱")
        void withUsers() {
            when(memberRepository.findByProjectId(1L)).thenReturn(List.of(
                    member(1L, 1L, 10L, ProjectMember.MemberRole.MEMBER),
                    member(2L, 1L, 20L, ProjectMember.MemberRole.ADMIN)));
            when(userRepository.findAllById(any())).thenReturn(List.of(
                    user(10L, "alice", "a@x.com"),
                    user(20L, "bob", "b@x.com")));

            ApiResponse<List<ProjectMemberDTO>> res = service.getProjectMembers(1L);

            assertEquals(200, res.getCode());
            assertEquals(2, res.getData().size());
            assertEquals("alice", res.getData().get(0).getUsername());
            assertEquals("b@x.com", res.getData().get(1).getEmail());
        }

        @Test
        @DisplayName("用户缺失时字段为 null")
        void missingUser() {
            when(memberRepository.findByProjectId(1L)).thenReturn(List.of(
                    member(1L, 1L, 10L, ProjectMember.MemberRole.MEMBER)));
            when(userRepository.findAllById(any())).thenReturn(Collections.emptyList());

            assertNull(service.getProjectMembers(1L).getData().get(0).getUsername());
        }
    }

    @Nested
    @DisplayName("getUserRole / isProjectAdmin")
    class RoleLookup {

        @Test
        @DisplayName("成员存在时返回角色")
        void roleFound() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L))
                    .thenReturn(Optional.of(member(5L, 1L, 2L, ProjectMember.MemberRole.MEMBER)));

            ApiResponse<ProjectMember.MemberRole> res = service.getUserRole(1L, 2L);

            assertEquals(200, res.getCode());
            assertEquals(ProjectMember.MemberRole.MEMBER, res.getData());
        }

        @Test
        @DisplayName("非成员返回错误")
        void notMember() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.getUserRole(1L, 2L).getCode());
        }

        @Test
        @DisplayName("查询异常兜底")
        void roleError() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getUserRole(1L, 2L).getCode());
        }

        @Test
        @DisplayName("管理员判定")
        void admin() {
            when(memberRepository.findByProjectIdAndUserIdAndRole(1L, 2L, ProjectMember.MemberRole.ADMIN))
                    .thenReturn(Optional.of(member(5L, 1L, 2L, ProjectMember.MemberRole.ADMIN)));
            assertTrue(service.isProjectAdmin(1L, 2L));

            when(memberRepository.findByProjectIdAndUserIdAndRole(1L, 3L, ProjectMember.MemberRole.ADMIN))
                    .thenReturn(Optional.empty());
            assertFalse(service.isProjectAdmin(1L, 3L));
        }
    }

    @Nested
    @DisplayName("异常兜底")
    class Errors {

        @Test
        @DisplayName("更新角色异常兜底")
        void updateError() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.updateMemberRole(1L, 2L, ProjectMember.MemberRole.MEMBER).getCode());
        }

        @Test
        @DisplayName("删除成员异常兜底")
        void removeError() {
            when(memberRepository.findByProjectIdAndUserId(1L, 2L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.removeProjectMember(1L, 2L).getCode());
        }

        @Test
        @DisplayName("查询成员列表异常兜底")
        void listError() {
            when(memberRepository.findByProjectId(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getProjectMembers(1L).getCode());
        }
    }
}
