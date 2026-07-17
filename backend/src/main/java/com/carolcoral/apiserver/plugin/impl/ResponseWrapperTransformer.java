/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.plugin.impl;

import com.carolcoral.apiserver.dto.MockRequest;
import com.carolcoral.apiserver.dto.MockResponseDTO;
import com.carolcoral.apiserver.plugin.CustomResponseTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 响应包装转换器
 * <p>
 * 将原始响应体包装在一个标准的外层结构中，添加统一的响应格式：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": <原始响应体>,
 *   "timestamp": 1234567890
 * }
 * </pre>
 * </p>
 * <p>
 * 使用方式：在接口的 customResponseHandler 字段中配置 "ResponseWrapperTransformer"
 * </p>
 *
 * @author carolcoral
 */
@Component
public class ResponseWrapperTransformer implements CustomResponseTransformer {

    private static final Logger log = LoggerFactory.getLogger(ResponseWrapperTransformer.class);

    @Override
    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,
                                      String apiName, String apiPath) {
        log.debug("ResponseWrapperTransformer: 包装接口 {} 的响应", apiName);

        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("code", mockResponse.getStatusCode());
        wrapped.put("message", "success");
        wrapped.put("data", mockResponse.getBody());
        wrapped.put("timestamp", System.currentTimeMillis());

        // 创建新的响应DTO，保留原有的statusCode和headers
        MockResponseDTO result = MockResponseDTO.builder()
                .statusCode(mockResponse.getStatusCode())
                .headers(mockResponse.getHeaders())
                .body(wrapped)
                .delay(mockResponse.getDelay())  // 保留原有延迟设置
                .build();

        return result;
    }

    @Override
    public String getName() {
        return "ResponseWrapperTransformer";
    }

    @Override
    public String getDescription() {
        return "将响应体包装为统一的 {code, message, data, timestamp} 格式";
    }
}
