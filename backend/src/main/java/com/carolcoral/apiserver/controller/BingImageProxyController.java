/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Bing 每日图片代理Controller
 * 转发前端请求到 Bing API，解决浏览器跨域问题
 *
 * @author carolcoral
 */
@Tag(name = "Bing图片代理", description = "Bing每日图片代理接口")
@RestController
@RequestMapping("/bing-hp")
public class BingImageProxyController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BingImageProxyController.class);

    private static final String BING_HP_URL = "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";

    private final RestTemplate restTemplate;

    public BingImageProxyController() {
        // 使用短超时的 RestTemplate，避免阻塞
        this.restTemplate = new RestTemplate();
        // 注意：RestTemplate 默认使用 SimpleClientHttpRequestFactory，超时通过系统属性设置
        System.setProperty("sun.net.client.defaultConnectTimeout", "2000");
        System.setProperty("sun.net.client.defaultReadTimeout", "2000");
    }

    /**
     * 代理获取 Bing 每日图片数据
     *
     * @return Bing HPImageArchive JSON 响应
     */
    @GetMapping
    @Operation(summary = "获取Bing每日图片")
    public ResponseEntity<String> getBingDailyImage() {
        try {
            log.debug("代理请求 Bing 每日图片 API");

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (compatible; ApiServer/1.0)");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BING_HP_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Bing 每日图片获取成功");
                return ResponseEntity
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response.getBody());
            } else {
                log.warn("Bing API 返回非成功状态码: {}", response.getStatusCode());
                return ResponseEntity
                        .status(HttpStatus.BAD_GATEWAY)
                        .body("{\"error\":\"Bing API returned non-success status\"}");
            }
        } catch (Exception e) {
            log.error("Bing 每日图片代理请求失败: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Failed to fetch Bing daily image\"}");
        }
    }
}
