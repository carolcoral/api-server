/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * MockResponseService 单元测试：按 ID 命中与未命中。
 */
@ExtendWith(MockitoExtension.class)
class MockResponseServiceTest {

    @Mock
    MockResponseRepository repository;

    @InjectMocks
    MockResponseService service;

    @Test
    @DisplayName("按 ID 命中返回实体")
    void found() {
        MockResponse r = new MockResponse();
        r.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertSame(r, service.getById(1L));
    }

    @Test
    @DisplayName("按 ID 未命中返回 null")
    void notFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertNull(service.getById(9L));
    }
}
