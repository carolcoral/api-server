/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权限扫描器
 * 扫描所有 Controller 上的 @PreAuthorize 注解，提取权限编码信息
 * 服务启动时自动加载并缓存，版本不变时内容不变
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Service
public class PermissionScanner {

    private static final Logger log = LoggerFactory.getLogger(PermissionScanner.class);

    /** 权限编码正则: hasAuthority('xxx:xxx') */
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    /** 缓存的扫描结果: code -> PermissionScanInfo */
    private volatile Map<String, PermissionScanInfo> scannedPermissions = new LinkedHashMap<>();

    /** 缓存的分组信息 */
    private volatile Set<String> scannedGroups = new LinkedHashSet<>();

    /** 缓存的权限名称信息: code -> 从 @Operation 提取的名称 */
    private volatile Map<String, String> scannedNames = new LinkedHashMap<>();

    /**
     * 扫描到的权限信息
     */
    public static class PermissionScanInfo {
        private String code;           // 权限编码: user:view
        private String name;           // 权限名称（从 @Operation 提取）
        private String groupName;      // 分组名称（从 @Tag 提取）
        private String type;           // PAGE / BUTTON
        private String controllerName; // 所在控制器
        private String methodName;     // 所在方法
        private String httpMethod;     // HTTP 方法: GET/POST/PUT/DELETE

        // Getters & Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getControllerName() { return controllerName; }
        public void setControllerName(String controllerName) { this.controllerName = controllerName; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getHttpMethod() { return httpMethod; }
        public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    }

    /**
     * 启动时自动扫描
     */
    @PostConstruct
    public void scanOnStartup() {
        try {
            scanPermissions();
            log.info("权限扫描完成: 共 {} 个权限编码, {} 个分组",
                scannedPermissions.size(), scannedGroups.size());
        } catch (Exception e) {
            log.error("权限扫描失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 扫描所有 Controller 中的 @PreAuthorize 注解
     */
    public synchronized void scanPermissions() throws Exception {
        Map<String, PermissionScanInfo> newPermissions = new LinkedHashMap<>();
        Set<String> newGroups = new LinkedHashSet<>();
        Map<String, String> newNames = new LinkedHashMap<>();

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory readerFactory = new CachingMetadataReaderFactory();

        // 扫描 com.carolcoral.apiserver 包下的所有类
        String packagePath = "com/carolcoral/apiserver";
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + packagePath + "/**/*.class";
        Resource[] resources = resolver.getResources(pattern);

        for (Resource resource : resources) {
            if (!resource.isReadable()) continue;

            try {
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();

                // 只扫描 @RestController 类
                if (!reader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                    continue;
                }

                Class<?> clazz = Class.forName(className);

                // 获取控制器级别的 @Tag 分组名称
                String controllerGroup = extractGroupName(clazz);
                String controllerTag = extractTagName(clazz);

                // 获取 @RequestMapping 前缀
                String mappingPrefix = extractMappingPrefix(clazz);

                // 扫描每个方法
                for (Method method : clazz.getDeclaredMethods()) {
                    PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);
                    if (preAuth == null) continue;

                    String expression = preAuth.value();
                    List<String> codes = extractAuthorityCodes(expression);
                    if (codes.isEmpty()) continue;

                    // 获取方法上的 @Operation 名称
                    String operationName = extractOperationName(method);
                    // 获取 HTTP 方法
                    String httpMethod = extractHttpMethod(method);

                    for (String code : codes) {
                        // 去重：同一个 code 只保留第一次扫描到的
                        if (newPermissions.containsKey(code)) continue;

                        PermissionScanInfo info = new PermissionScanInfo();
                        info.setCode(code);
                        info.setName(buildPermissionName(code, operationName, controllerTag));
                        info.setGroupName(controllerGroup);
                        info.setType(autoDetectType(code));
                        info.setControllerName(clazz.getSimpleName());
                        info.setMethodName(method.getName());
                        info.setHttpMethod(httpMethod);

                        newPermissions.put(code, info);
                        newNames.put(code, info.getName());
                        if (StringUtils.hasText(controllerGroup)) {
                            newGroups.add(controllerGroup);
                        }
                    }
                }

            } catch (Exception e) {
                log.debug("扫描类失败: {}, 原因: {}", resource.getFilename(), e.getMessage());
            }
        }

        this.scannedPermissions = Collections.unmodifiableMap(newPermissions);
        this.scannedGroups = Collections.unmodifiableSet(newGroups);
        this.scannedNames = Collections.unmodifiableMap(newNames);
    }

    /**
     * 获取扫描到的所有权限信息
     */
    public List<PermissionScanInfo> getAllScannedPermissions() {
        return new ArrayList<>(scannedPermissions.values());
    }

    /**
     * 获取扫描到的所有权限编码（去重）
     */
    public Set<String> getAllScannedCodes() {
        return new LinkedHashSet<>(scannedPermissions.keySet());
    }

    /**
     * 获取扫描到的所有分组名称
     */
    public Set<String> getAllScannedGroups() {
        return new LinkedHashSet<>(scannedGroups);
    }

    /**
     * 获取扫描到的权限名称映射
     */
    public Map<String, String> getAllScannedNames() {
        return new LinkedHashMap<>(scannedNames);
    }

    /**
     * 根据编码获取扫描信息
     */
    public PermissionScanInfo getScannedPermission(String code) {
        return scannedPermissions.get(code);
    }

    /**
     * 检查编码是否在扫描结果中
     */
    public boolean isScannedCode(String code) {
        return scannedPermissions.containsKey(code);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从 @Tag 注解提取分组名称
     */
    private String extractGroupName(Class<?> clazz) {
        Tag tag = clazz.getAnnotation(Tag.class);
        if (tag != null && StringUtils.hasText(tag.name())) {
            return tag.name();
        }
        return null;
    }

    /**
     * 从 @Tag 注解提取描述（用于构建权限名称）
     */
    private String extractTagName(Class<?> clazz) {
        Tag tag = clazz.getAnnotation(Tag.class);
        if (tag != null) {
            if (StringUtils.hasText(tag.name())) return tag.name();
            if (StringUtils.hasText(tag.description())) return tag.description();
        }
        return clazz.getSimpleName();
    }

    /**
     * 提取类上的 @RequestMapping 前缀
     */
    private String extractMappingPrefix(Class<?> clazz) {
        RequestMapping rm = clazz.getAnnotation(RequestMapping.class);
        if (rm != null && rm.value().length > 0) {
            return rm.value()[0];
        }
        return "";
    }

    /**
     * 从 @PreAuthorize 表达式中提取 hasAuthority('xxx') 中的权限编码
     */
    private List<String> extractAuthorityCodes(String expression) {
        List<String> codes = new ArrayList<>();
        Matcher matcher = AUTHORITY_PATTERN.matcher(expression);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    /**
     * 从 @Operation 注解提取接口名称
     */
    private String extractOperationName(Method method) {
        Operation op = method.getAnnotation(Operation.class);
        if (op != null && StringUtils.hasText(op.summary())) {
            return op.summary();
        }
        return null;
    }

    /**
     * 提取 HTTP 方法类型
     */
    private String extractHttpMethod(Method method) {
        if (method.getAnnotation(GetMapping.class) != null) return "GET";
        if (method.getAnnotation(PostMapping.class) != null) return "POST";
        if (method.getAnnotation(PutMapping.class) != null) return "PUT";
        if (method.getAnnotation(DeleteMapping.class) != null) return "DELETE";
        return "";
    }

    /**
     * 根据权限编码自动识别类型
     * :view -> PAGE，其他 -> BUTTON
     */
    private String autoDetectType(String code) {
        if (code.endsWith(":view")) {
            return "PAGE";
        }
        return "BUTTON";
    }

    /**
     * 构建权限名称
     * 格式: 分组-操作描述（如: 项目管理-创建）
     */
    private String buildPermissionName(String code, String operationName, String controllerTag) {
        // 如果 @Operation 有明确名称，优先使用
        if (StringUtils.hasText(operationName)) {
            return operationName;
        }

        // 否则根据编码自动生成: 分组-操作
        String[] parts = code.split(":");
        if (parts.length >= 2) {
            String module = parts[0];
            String action = parts[parts.length - 1];
            String actionName = mapActionName(action);
            return controllerTag + "-" + actionName;
        }

        return code;
    }

    /**
     * 操作编码映射为中文名称
     */
    private String mapActionName(String action) {
        return switch (action) {
            case "view" -> "页面访问";
            case "create" -> "创建";
            case "edit" -> "编辑";
            case "delete" -> "删除";
            case "toggle" -> "启用禁用";
            case "assign" -> "权限分配";
            case "set-default" -> "设置默认";
            case "test" -> "测试连通性";
            case "import_swagger" -> "导入Swagger";
            case "export_swagger" -> "导出Swagger";
            case "export_data" -> "导出项目数据";
            case "import_data" -> "导入项目数据";
            case "template_engine" -> "模板引擎";
            case "view_all" -> "查看全部";
            case "backup" -> "备份导出";
            case "restore" -> "数据恢复";
            case "basic" -> "基础设置";
            case "security" -> "安全配置";
            case "jwt" -> "JWT配置";
            case "mock" -> "Mock配置";
            case "announcement" -> "公告管理";
            case "system" -> "系统信息";
            case "footer" -> "页脚设置";
            case "registration" -> "注册设置";
            case "subscribe" -> "订阅管理";
            case "key-manage" -> "密钥管理";
            default -> action;
        };
    }
}
