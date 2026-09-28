/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.SystemAnnouncementDTO;
import com.carolcoral.apiserver.entity.SystemAnnouncement;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.SystemAnnouncementRepository;
import com.carolcoral.apiserver.repository.UserRepository;
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
 * SystemAnnouncementService 单元测试：唯一启用互斥、CRUD、创建人装配与异常兜底。
 */
@ExtendWith(MockitoExtension.class)
class SystemAnnouncementServiceTest {

    @Mock
    SystemAnnouncementRepository repository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    SystemAnnouncementService service;

    private SystemAnnouncement announcement(Long id, boolean enabled) {
        return SystemAnnouncement.builder()
                .id(id).title("t").content("c").enabled(enabled)
                .priority("NORMAL").createUserId(1L).build();
    }

    private SystemAnnouncementDTO dto(String title, Boolean enabled) {
        SystemAnnouncementDTO d = new SystemAnnouncementDTO();
        d.setTitle(title);
        d.setContent("c");
        d.setEnabled(enabled);
        return d;
    }

    @Nested
    @DisplayName("查询")
    class Query {

        @Test
        @DisplayName("存在启用公告时返回 DTO 并装配创建人")
        void enabledFound() {
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(announcement(1L, true)));
            User u = new User();
            u.setUsername("alice");
            when(userRepository.findById(1L)).thenReturn(Optional.of(u));

            ApiResponse<SystemAnnouncementDTO> res = service.getEnabledAnnouncement();

            assertEquals(200, res.getCode());
            assertEquals("alice", res.getData().getCreateBy());
        }

        @Test
        @DisplayName("无启用公告时返回 null 数据")
        void noneEnabled() {
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.empty());
            assertNull(service.getEnabledAnnouncement().getData());
        }

        @Test
        @DisplayName("查询异常兜底")
        void error() {
            when(repository.findEnabledAnnouncement()).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getEnabledAnnouncement().getCode());
        }

        @Test
        @DisplayName("创建人缺失时 createBy 为 null")
        void missingCreator() {
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(announcement(1L, true)));
            when(userRepository.findById(1L)).thenReturn(Optional.empty());
            assertNull(service.getEnabledAnnouncement().getData().getCreateBy());
        }

        @Test
        @DisplayName("getAnnouncementById 命中与未命中")
        void byId() {
            when(repository.findById(1L)).thenReturn(Optional.of(announcement(1L, true)));
            assertEquals(200, service.getAnnouncementById(1L).getCode());

            when(repository.findById(2L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.getAnnouncementById(2L).getCode());
        }
    }

    @Nested
    @DisplayName("createAnnouncement")
    class Create {

        @Test
        @DisplayName("启用新公告时先禁用既有启用公告")
        void disablesExisting() {
            SystemAnnouncement existing = announcement(9L, true);
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            ApiResponse<SystemAnnouncementDTO> res = service.createAnnouncement(dto("new", true));

            assertEquals(200, res.getCode());
            assertFalse(existing.getEnabled());
            verify(repository).save(existing);
        }

        @Test
        @DisplayName("未指定启用时默认启用")
        void defaultsEnabled() {
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            ApiResponse<SystemAnnouncementDTO> res = service.createAnnouncement(dto("new", null));
            assertEquals(200, res.getCode());
            assertTrue(res.getData().getEnabled());
        }

        @Test
        @DisplayName("保存异常兜底")
        void error() {
            when(repository.save(any())).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.createAnnouncement(dto("new", false)).getCode());
        }
    }

    @Nested
    @DisplayName("updateAnnouncement")
    class Update {

        @Test
        @DisplayName("公告不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updateAnnouncement(1L, dto("t", true)).getCode());
        }

        @Test
        @DisplayName("由禁用改为启用时禁用其他启用公告")
        void disablesOthers() {
            SystemAnnouncement target = announcement(1L, false);
            SystemAnnouncement other = announcement(2L, true);
            when(repository.findById(1L)).thenReturn(Optional.of(target));
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(other));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.updateAnnouncement(1L, dto("t", true)).getCode());
            assertFalse(other.getEnabled());
            assertTrue(target.getEnabled());
        }

        @Test
        @DisplayName("启用时若命中的就是自身则不重复禁用")
        void selfNotDisabled() {
            SystemAnnouncement target = announcement(1L, false);
            when(repository.findById(1L)).thenReturn(Optional.of(target));
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(target));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.updateAnnouncement(1L, dto("t", true)).getCode());
            assertTrue(target.getEnabled());
        }
    }

    @Nested
    @DisplayName("deleteAnnouncement")
    class Delete {

        @Test
        @DisplayName("不存在")
        void missing() {
            when(repository.existsById(1L)).thenReturn(false);
            assertNotEquals(200, service.deleteAnnouncement(1L).getCode());
        }

        @Test
        @DisplayName("删除成功")
        void success() {
            when(repository.existsById(1L)).thenReturn(true);
            assertEquals(200, service.deleteAnnouncement(1L).getCode());
            verify(repository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("toggleAnnouncementStatus")
    class Toggle {

        @Test
        @DisplayName("不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.toggleAnnouncementStatus(1L, true).getCode());
        }

        @Test
        @DisplayName("启用时禁用其他")
        void enableDisablesOthers() {
            SystemAnnouncement target = announcement(1L, false);
            SystemAnnouncement other = announcement(2L, true);
            when(repository.findById(1L)).thenReturn(Optional.of(target));
            when(repository.findEnabledAnnouncement()).thenReturn(Optional.of(other));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.toggleAnnouncementStatus(1L, true).getCode());
            assertFalse(other.getEnabled());
            assertTrue(target.getEnabled());
        }

        @Test
        @DisplayName("关闭时仅更新自身")
        void disable() {
            SystemAnnouncement target = announcement(1L, true);
            when(repository.findById(1L)).thenReturn(Optional.of(target));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertEquals(200, service.toggleAnnouncementStatus(1L, false).getCode());
            assertFalse(target.getEnabled());
            verify(repository, never()).findEnabledAnnouncement();
        }
    }

    @Nested
    @DisplayName("getAllAnnouncements / 异常兜底")
    class Paged {

        @Test
        @DisplayName("分页返回 DTO 列表")
        void paged() {
            org.springframework.data.domain.Page<SystemAnnouncement> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(announcement(1L, true)));
            when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            ApiResponse<com.carolcoral.apiserver.dto.PageResult<SystemAnnouncementDTO>> res =
                    service.getAllAnnouncements(org.springframework.data.domain.PageRequest.of(0, 10));

            assertEquals(200, res.getCode());
            assertEquals(1, res.getData().getContent().size());
        }

        @Test
        @DisplayName("分页异常兜底")
        void pagedError() {
            when(repository.findAll(any(org.springframework.data.domain.Pageable.class)))
                    .thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getAllAnnouncements(org.springframework.data.domain.PageRequest.of(0, 10)).getCode());
        }

        @Test
        @DisplayName("更新公告异常兜底")
        void updateError() {
            when(repository.findById(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.updateAnnouncement(1L, dto("t", true)).getCode());
        }

        @Test
        @DisplayName("删除公告异常兜底")
        void deleteError() {
            when(repository.existsById(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.deleteAnnouncement(1L).getCode());
        }

        @Test
        @DisplayName("切换状态异常兜底")
        void toggleError() {
            when(repository.findById(1L)).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.toggleAnnouncementStatus(1L, true).getCode());
        }
    }
}
