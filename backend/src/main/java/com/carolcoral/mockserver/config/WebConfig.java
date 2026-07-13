/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * <p>
 * 配置静态资源处理和视图控制器
 * 用于支持 Docker 容器中的前后端一体化部署
 * </p>
 *
 * @author carolcoral
 * @since 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源处理器
     * <p>
     * 支持以下情况：
     * 1. Docker 容器部署时，前端静态文件位于 /app/frontend/dist
     * 2. 本地开发时，前端通过 Vite 开发服务器单独运行
     * </p>
     *
     * @param registry ResourceHandlerRegistry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 前端静态资源路径映射
        // 开发环境：前端通过 Vite 开发服务器运行，不需要此配置
        // 生产环境（Docker）：前端静态文件由后端服务
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/", "file:/app/frontend/dist/assets/");
        
        registry.addResourceHandler("/index.html", "/favicon.ico", "/robots.txt")
                .addResourceLocations("classpath:/static/", "file:/app/frontend/dist/");
        
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "file:/app/frontend/dist/")
                .resourceChain(true);
    }

    /**
     * 配置视图控制器
     * <p>
     * 将根路径和常见前端路由转发到 index.html
     * 支持 Vue Router 的 history 模式
     * </p>
     *
     * @param registry ViewControllerRegistry
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径转发到 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        
        // SPA 常见路由转发到 index.html（Vue Router history 模式）
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/register").setViewName("forward:/index.html");
        registry.addViewController("/forgot-password").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/home").setViewName("forward:/index.html");
        registry.addViewController("/projects").setViewName("forward:/index.html");
        registry.addViewController("/apis").setViewName("forward:/index.html");
        registry.addViewController("/users").setViewName("forward:/index.html");
        registry.addViewController("/settings").setViewName("forward:/index.html");
        registry.addViewController("/statistics").setViewName("forward:/index.html");
        registry.addViewController("/debug-panel").setViewName("forward:/index.html");
        registry.addViewController("/guide").setViewName("forward:/index.html");
        registry.addViewController("/profile").setViewName("forward:/index.html");
        registry.addViewController("/code-templates").setViewName("forward:/index.html");
        registry.addViewController("/changelog").setViewName("forward:/index.html");
        registry.addViewController("/email-templates").setViewName("forward:/index.html");
        registry.addViewController("/ai-settings").setViewName("forward:/index.html");
        registry.addViewController("/ai-chat").setViewName("forward:/index.html");
        registry.addViewController("/roles").setViewName("forward:/index.html");
        registry.addViewController("/permissions").setViewName("forward:/index.html");
        registry.addViewController("/ops-monitor").setViewName("forward:/index.html");
    }
}
