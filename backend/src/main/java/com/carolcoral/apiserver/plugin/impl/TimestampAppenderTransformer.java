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
 * 时间戳追加转换器
 * <p>
 * 在响应体中自动添加 serverTime 字段（服务器当前时间戳）。
 * 如果响应体是Map/JSON对象，则追加字段；如果是字符串，则包裹为对象。
 * </p>
 * <p>
 * 使用方式：在接口的 customResponseHandler 字段中配置 "TimestampAppenderTransformer"
 * </p>
 *
 * @author carolcoral
 */
@Component
public class TimestampAppenderTransformer implements CustomResponseTransformer {

    private static final Logger log = LoggerFactory.getLogger(TimestampAppenderTransformer.class);

    @Override
    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,
                                      String apiName, String apiPath) {
        log.debug("TimestampAppenderTransformer: 为接口 {} 的响应追加时间戳", apiName);

        Object body = mockResponse.getBody();
        Object newBody;

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = new LinkedHashMap<>((Map<String, Object>) body);
            bodyMap.put("serverTime", System.currentTimeMillis());
            bodyMap.put("serverTimeStr", java.time.Instant.now().toString());
            newBody = bodyMap;
        } else {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("data", body);
            wrapped.put("serverTime", System.currentTimeMillis());
            wrapped.put("serverTimeStr", java.time.Instant.now().toString());
            newBody = wrapped;
        }

        MockResponseDTO result = MockResponseDTO.builder()
                .statusCode(mockResponse.getStatusCode())
                .headers(mockResponse.getHeaders())
                .body(newBody)
                .delay(mockResponse.getDelay())
                .build();

        return result;
    }

    @Override
    public String getName() {
        return "TimestampAppenderTransformer";
    }

    @Override
    public String getDescription() {
        return "在响应体中自动添加 serverTime 服务器时间戳字段";
    }
}
