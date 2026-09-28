/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ResponseRequestParamDTO;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.ResponseRequestParamRepository;
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
 * ResponseRequestParamService 单元测试：按响应过滤、创建/更新/删除与默认响应保护。
 */
@ExtendWith(MockitoExtension.class)
class ResponseRequestParamServiceTest {

    @Mock
    ResponseRequestParamRepository repository;

    @Mock
    MockResponseService mockResponseService;

    @InjectMocks
    ResponseRequestParamService service;

    private MockResponse response(Long id, boolean isDefault) {
        MockResponse r = new MockResponse();
        r.setId(id);
        r.setIsDefault(isDefault);
        return r;
    }

    private ResponseRequestParam param(Long id, MockResponse response, String name) {
        ResponseRequestParam p = new ResponseRequestParam();
        p.setId(id);
        p.setMockResponse(response);
        p.setParamName(name);
        p.setParamType(ResponseRequestParam.ParamType.REQUEST_BODY);
        p.setParamValue("v");
        p.setRequired(true);
        return p;
    }

    private ResponseRequestParamDTO dto(String name) {
        ResponseRequestParamDTO d = new ResponseRequestParamDTO();
        d.setParamName(name);
        d.setParamType("REQUEST_BODY");
        d.setParamValue("v");
        d.setRequired(true);
        return d;
    }

    @Nested
    @DisplayName("getParamsByResponseId")
    class ListParams {

        @Test
        @DisplayName("仅返回匹配 responseId 的参数")
        void filters() {
            when(repository.findAll()).thenReturn(List.of(
                    param(1L, response(10L, false), "a"),
                    param(2L, response(20L, false), "b")));

            ApiResponse<List<ResponseRequestParamDTO>> res = service.getParamsByResponseId(10L);

            assertEquals(200, res.getCode());
            assertEquals(1, res.getData().size());
            assertEquals("a", res.getData().get(0).getParamName());
        }

        @Test
        @DisplayName("mockResponse 为 null 的条目被忽略")
        void nullResponse() {
            when(repository.findAll()).thenReturn(List.of(param(1L, null, "a")));
            assertTrue(service.getParamsByResponseId(1L).getData().isEmpty());
        }

        @Test
        @DisplayName("仓库异常兜底")
        void error() {
            when(repository.findAll()).thenThrow(new RuntimeException("boom"));
            assertNotEquals(200, service.getParamsByResponseId(1L).getCode());
        }
    }

    @Nested
    @DisplayName("createParam")
    class Create {

        @Test
        @DisplayName("响应不存在")
        void responseMissing() {
            when(mockResponseService.getById(1L)).thenReturn(null);
            assertNotEquals(200, service.createParam(1L, dto("a")).getCode());
        }

        @Test
        @DisplayName("默认响应不允许设置请求参数")
        void defaultResponseProtected() {
            when(mockResponseService.getById(1L)).thenReturn(response(1L, true));
            assertNotEquals(200, service.createParam(1L, dto("a")).getCode());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("创建成功并回填 DTO")
        void success() {
            when(mockResponseService.getById(1L)).thenReturn(response(1L, false));
            when(repository.save(any())).thenAnswer(inv -> {
                ResponseRequestParam p = inv.getArgument(0);
                p.setId(99L);
                return p;
            });

            ApiResponse<ResponseRequestParamDTO> res = service.createParam(1L, dto("a"));

            assertEquals(200, res.getCode());
            assertEquals(99L, res.getData().getId());
            assertEquals("REQUEST_BODY", res.getData().getParamType());
        }
    }

    @Nested
    @DisplayName("updateParam")
    class Update {

        @Test
        @DisplayName("参数不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.updateParam(1L, dto("a")).getCode());
        }

        @Test
        @DisplayName("默认响应的参数不可修改")
        void defaultProtected() {
            when(repository.findById(1L))
                    .thenReturn(Optional.of(param(1L, response(10L, true), "old")));
            assertNotEquals(200, service.updateParam(1L, dto("new")).getCode());
        }

        @Test
        @DisplayName("更新成功")
        void success() {
            ResponseRequestParam existing = param(1L, response(10L, false), "old");
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            ApiResponse<ResponseRequestParamDTO> res = service.updateParam(1L, dto("new"));

            assertEquals(200, res.getCode());
            assertEquals("new", existing.getParamName());
        }

        @Test
        @DisplayName("paramType 为 null 时保持原类型")
        void keepType() {
            ResponseRequestParam existing = param(1L, response(10L, false), "old");
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

            ResponseRequestParamDTO d = dto("new");
            d.setParamType(null);
            service.updateParam(1L, d);

            assertEquals(ResponseRequestParam.ParamType.REQUEST_BODY, existing.getParamType());
        }
    }

    @Nested
    @DisplayName("deleteParam")
    class Delete {

        @Test
        @DisplayName("参数不存在")
        void missing() {
            when(repository.findById(1L)).thenReturn(Optional.empty());
            assertNotEquals(200, service.deleteParam(1L).getCode());
        }

        @Test
        @DisplayName("默认响应的参数不可删除")
        void defaultProtected() {
            when(repository.findById(1L))
                    .thenReturn(Optional.of(param(1L, response(10L, true), "x")));
            assertNotEquals(200, service.deleteParam(1L).getCode());
            verify(repository, never()).deleteById(any());
        }

        @Test
        @DisplayName("删除成功")
        void success() {
            when(repository.findById(1L))
                    .thenReturn(Optional.of(param(1L, response(10L, false), "x")));
            assertEquals(200, service.deleteParam(1L).getCode());
            verify(repository).deleteById(1L);
        }
    }
}
