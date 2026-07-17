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
import java.util.Set;

/**
 * 字段脱敏转换器
 * <p>
 * 自动将响应体中的敏感字段（如 password、secret、token、phone、idCard 等）进行脱敏处理。
 * 脱敏规则：保留前2位和后2位，中间用星号替代。
 * </p>
 * <p>
 * 使用方式：在接口的 customResponseHandler 字段中配置 "FieldMaskTransformer"
 * </p>
 *
 * @author carolcoral
 */
@Component
public class FieldMaskTransformer implements CustomResponseTransformer {

    private static final Logger log = LoggerFactory.getLogger(FieldMaskTransformer.class);

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "pwd",
            "secret", "secretKey", "secret_key",
            "token", "accessToken", "access_token", "refreshToken", "refresh_token",
            "phone", "mobile", "telephone",
            "idCard", "id_card", "idNumber", "id_number",
            "bankCard", "bank_card", "bankAccount", "bank_account"
    );

    @Override
    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,
                                      String apiName, String apiPath) {
        log.debug("FieldMaskTransformer: 对接口 {} 的响应进行敏感字段脱敏", apiName);

        Object body = mockResponse.getBody();
        Object maskedBody = maskSensitiveFields(body);

        MockResponseDTO result = MockResponseDTO.builder()
                .statusCode(mockResponse.getStatusCode())
                .headers(mockResponse.getHeaders())
                .body(maskedBody)
                .delay(mockResponse.getDelay())
                .build();

        return result;
    }

    @SuppressWarnings("unchecked")
    private Object maskSensitiveFields(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (isSensitiveField(key) && value instanceof String) {
                    result.put(key, maskValue((String) value));
                } else if (value instanceof Map || value instanceof java.util.List) {
                    result.put(key, maskSensitiveFields(value));
                } else {
                    result.put(key, value);
                }
            }
            return result;
        } else if (obj instanceof java.util.List) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (Object item : (java.util.List<?>) obj) {
                list.add(maskSensitiveFields(item));
            }
            return list;
        }
        return obj;
    }

    private boolean isSensitiveField(String fieldName) {
        String lower = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lower.equals(sensitive) || lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    @Override
    public String getName() {
        return "FieldMaskTransformer";
    }

    @Override
    public String getDescription() {
        return "自动脱敏敏感字段（password/token/phone/idCard等），保留前2后2位";
    }
}
