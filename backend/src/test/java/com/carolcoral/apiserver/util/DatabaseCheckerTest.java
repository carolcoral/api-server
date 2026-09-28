/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

/**
 * DatabaseChecker 单元测试：启动期用户检查的各个分支
 *
 * @author carolcoral
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseCheckerTest {

    @Mock
    private UserRepository userRepository;

    private DatabaseChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DatabaseChecker(userRepository);
    }

    private User user(Long id, String name, String email) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setEmail(email);
        return u;
    }

    @Test
    @DisplayName("无用户时输出告警但不抛异常")
    void noUsers() {
        when(userRepository.count()).thenReturn(0L);
        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    @DisplayName("存在 admin 用户时正常完成")
    void hasAdminUser() {
        when(userRepository.count()).thenReturn(2L);
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "admin", "a@b.c"), user(2L, "bob", "b@b.c")));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", "a@b.c")));

        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    @DisplayName("有用户但缺少 admin 时输出告警")
    void missingAdminUser() {
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAll()).thenReturn(List.of(user(2L, "bob", "b@b.c")));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    @DisplayName("仓储异常时被捕获，不阻断应用启动")
    void repositoryFailureIsCaught() {
        when(userRepository.count()).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> checker.run());
    }
}
