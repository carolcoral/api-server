/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.MockRequest;
import com.carolcoral.apiserver.dto.MockResponseDTO;
import com.carolcoral.apiserver.service.MockService;
import com.carolcoral.apiserver.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MockController 单元测试：路径解析、请求信息采集、响应构建与延迟保护
 *
 * @author carolcoral
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockControllerTest {

    @Mock
    private MockService mockService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private HttpServletRequest request;

    private MockController controller;

    @BeforeEach
    void setUp() throws IOException {
        controller = new MockController(mockService, systemConfigService);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaderNames()).thenReturn(emptyEnumeration());
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(request.getRequestURI()).thenReturn("/api/api-server/demo/users");
        when(systemConfigService.getConfig(anyString())).thenReturn(null);
    }

    private Enumeration<String> emptyEnumeration() {
        return new Vector<String>().elements();
    }

    private Enumeration<String> enumeration(String... values) {
        return new Vector<>(Arrays.asList(values)).elements();
    }

    private MockResponseDTO okResponse() {
        return MockResponseDTO.builder().statusCode(200).body("ok").build();
    }

    /** 抓取传给 MockService 的 MockRequest，用于验证路径解析结果 */
    private MockRequest captureMockRequest() {
        ArgumentCaptor<MockRequest> captor = ArgumentCaptor.forClass(MockRequest.class);
        verify(mockService).handleMockRequest(captor.capture(), any());
        return captor.getValue();
    }

    @Nested
    @DisplayName("路径解析")
    class PathParsing {

        private void assertParsedPath(String requestUri, String expectedPath) {
            when(request.getRequestURI()).thenReturn(requestUri);
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertEquals(expectedPath, captureMockRequest().getPath());
        }

        @Test
        @DisplayName("标准前缀下的子路径被保留")
        void standardPrefixPath() {
            assertParsedPath("/api/api-server/demo/users/1", "/users/1");
        }

        @Test
        @DisplayName("仅项目根路径时解析为 /")
        void bareProjectPath() {
            assertParsedPath("/api/api-server/demo", "/");
        }

        @Test
        @DisplayName("无项目编码前缀时按 api-server 后内容解析")
        void withoutApiServerPrefix() {
            assertParsedPath("/api/api-server/", "/");
        }

        @Test
        @DisplayName("其他完整路径原样解析")
        void otherPath() {
            assertParsedPath("/other/thing", "/other/thing");
        }

        @Test
        @DisplayName("servletPath 形态的路径同样可解析")
        void servletPathForm() {
            when(request.getRequestURI()).thenReturn("/ignored");
            when(request.getServletPath()).thenReturn("/api-server/demo/items");
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertEquals("/api-server/demo/items", captureMockRequest().getPath());
        }
    }

    @Nested
    @DisplayName("请求信息采集")
    class RequestCollection {

        @Test
        @DisplayName("项目编码与方法被正确透传")
        void projectAndMethod() {
            when(request.getMethod()).thenReturn("POST");
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("ecmall", request);

            MockRequest captured = captureMockRequest();
            assertEquals("ecmall", captured.getProjectCode());
            assertEquals("POST", captured.getMethod());
        }

        @Test
        @DisplayName("请求头被完整收集")
        void headersCollected() {
            when(request.getHeaderNames()).thenReturn(enumeration("X-Trace-Id"));
            when(request.getHeader("X-Trace-Id")).thenReturn("abc123");
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertEquals("abc123", captureMockRequest().getHeaders().get("X-Trace-Id"));
        }

        @Test
        @DisplayName("单值参数存字符串，多值参数存数组")
        void paramsCollected() {
            Map<String, String[]> params = new HashMap<>();
            params.put("single", new String[]{"v"});
            params.put("multi", new String[]{"a", "b"});
            when(request.getParameterMap()).thenReturn(params);
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            MockRequest captured = captureMockRequest();
            assertEquals("v", captured.getParams().get("single"));
            assertArrayEquals(new String[]{"a", "b"}, (String[]) captured.getParams().get("multi"));
        }

        @Test
        @DisplayName("合法 JSON 请求体解析为对象")
        void jsonBodyParsed() throws IOException {
            when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"name\":\"x\"}")));
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertInstanceOf(Map.class, captureMockRequest().getBody());
        }

        @Test
        @DisplayName("非 JSON 请求体按字符串保存")
        void plainBodyKeptAsString() throws IOException {
            when(request.getReader()).thenReturn(new BufferedReader(new StringReader("raw-text")));
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertEquals("raw-text", captureMockRequest().getBody());
        }

        @Test
        @DisplayName("空请求体时不设置 body")
        void emptyBodyIgnored() throws IOException {
            when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            controller.handleMockRequest("demo", request);

            assertNull(captureMockRequest().getBody());
        }

        @Test
        @DisplayName("读取请求体异常时被捕获，不影响后续处理")
        void bodyReadFailureIsCaught() throws IOException {
            when(request.getReader()).thenThrow(new IOException("stream closed"));
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("响应构建")
    class ResponseBuilding {

        @Test
        @DisplayName("状态码、响应头与响应体被正确写入")
        void buildResponse() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Custom", "1");
            MockResponseDTO dto = MockResponseDTO.builder()
                    .statusCode(201).headers(headers).body(Collections.singletonMap("k", "v")).build();
            when(mockService.handleMockRequest(any(), any())).thenReturn(dto);

            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("1", response.getHeaders().getFirst("X-Custom"));
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("未指定 Content-Type 时补默认 JSON 头")
        void defaultContentType() {
            when(mockService.handleMockRequest(any(), any())).thenReturn(okResponse());

            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);

            assertEquals("application/json;charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        }

        @Test
        @DisplayName("已指定 Content-Type 时不覆盖")
        void customContentTypePreserved() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/plain");
            MockResponseDTO dto = MockResponseDTO.builder().statusCode(200).headers(headers).body("x").build();
            when(mockService.handleMockRequest(any(), any())).thenReturn(dto);

            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);

            assertEquals("text/plain", response.getHeaders().getFirst("Content-Type"));
        }

        @Test
        @DisplayName("响应延迟被限制在系统配置上限内")
        void delayCappedByConfig() {
            when(systemConfigService.getConfig("maxResponseDelay")).thenReturn("1");
            MockResponseDTO dto = MockResponseDTO.builder().statusCode(200).body("ok").delay(5000).build();
            when(mockService.handleMockRequest(any(), any())).thenReturn(dto);

            long start = System.currentTimeMillis();
            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);
            long cost = System.currentTimeMillis() - start;

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(cost < 3000, "延迟未被上限截断，实际耗时 " + cost + "ms");
        }

        @Test
        @DisplayName("配置值非法时回退默认上限，不影响响应")
        void invalidDelayConfigFallsBack() {
            when(systemConfigService.getConfig("maxResponseDelay")).thenReturn("not-a-number");
            MockResponseDTO dto = MockResponseDTO.builder().statusCode(200).body("ok").delay(0).build();
            when(mockService.handleMockRequest(any(), any())).thenReturn(dto);

            assertEquals(HttpStatus.OK, controller.handleMockRequest("demo", request).getStatusCode());
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("服务层异常返回 500 与统一错误体")
        void serviceFailureReturns500() {
            when(mockService.handleMockRequest(any(), any())).thenThrow(new RuntimeException("boom"));

            ResponseEntity<Object> response = controller.handleMockRequest("demo", request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            assertNotNull(body);
            assertEquals(500, body.get("code"));
            assertNotNull(body.get("timestamp"));
        }
    }
}
