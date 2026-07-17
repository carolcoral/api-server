/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.util.DatabaseDialectProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息控制器
 *
 * @author carolcoral
 */
@Tag(name = "系统信息", description = "系统信息相关接口")
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemInfoController.class);

    /** 缓存的版本号，避免重复解析 POM */
    private static volatile String cachedVersion;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired
    private DatabaseDialectProvider databaseDialectProvider;

    /**
     * 从 META-INF/maven 目录下的 pom.properties 读取版本号
     * 优先从 pom.properties 读取，回退到 pom.xml 解析
     */
    private static String resolveAppVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        // 方式1：从 pom.properties 读取（Maven 打包后自动生成）
        try {
            ClassPathResource resource = new ClassPathResource("META-INF/maven/com.carolcoral/api-server/pom.properties");
            if (resource.exists()) {
                java.util.Properties props = new java.util.Properties();
                try (InputStream is = resource.getInputStream()) {
                    props.load(is);
                }
                String version = props.getProperty("version");
                if (version != null && !version.isEmpty()) {
                    cachedVersion = version;
                    log.info("从 pom.properties 读取版本号: {}", version);
                    return version;
                }
            }
        } catch (Exception e) {
            log.debug("从 pom.properties 读取版本号失败: {}", e.getMessage());
        }

        // 方式2：从 pom.xml 解析
        try {
            ClassPathResource resource = new ClassPathResource("META-INF/maven/com.carolcoral/api-server/pom.xml");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                    var parent = doc.getDocumentElement();
                    // 如果是子模块，读取 <parent><version>，否则读取 <version>
                    var versionNode = parent.getElementsByTagName("version").item(0);
                    if (versionNode != null) {
                        String version = versionNode.getTextContent().trim();
                        if (!version.isEmpty()) {
                            cachedVersion = version;
                            log.info("从 pom.xml 读取版本号: {}", version);
                            return version;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从 pom.xml 读取版本号失败: {}", e.getMessage());
        }

        // 兜底：尝试从 MANIFEST.MF 获取
        try {
            String implVersion = SystemInfoController.class.getPackage().getImplementationVersion();
            if (implVersion != null && !implVersion.isEmpty()) {
                cachedVersion = implVersion;
                log.info("从 MANIFEST.MF 读取版本号: {}", implVersion);
                return implVersion;
            }
        } catch (Exception ignored) {
        }

        cachedVersion = "unknown";
        log.warn("无法读取版本号，使用默认值 unknown");
        return "unknown";
    }

    /**
     * 获取应用版本号
     *
     * @return 版本号信息
     */
    @Operation(summary = "获取应用版本号", description = "从 POM 文件读取当前应用版本号")
    @GetMapping("/version")
    public ApiResponse<Map<String, String>> getVersion() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("version", resolveAppVersion());
        result.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());
        result.put("appName", "API Server");
        return ApiResponse.success(result);
    }

    /**
     * 获取系统信息
     *
     * @return 系统信息
     */
    @Operation(summary = "获取系统信息", description = "获取服务器的系统信息、JVM信息、内存和CPU使用情况")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:system')")
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            Runtime runtime = Runtime.getRuntime();

            // ========== 系统信息 ==========
            info.put("osName", System.getProperty("os.name"));
            info.put("osVersion", System.getProperty("os.version"));
            info.put("osArch", System.getProperty("os.arch"));
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));
            info.put("javaHome", System.getProperty("java.home"));
            info.put("userName", System.getProperty("user.name"));
            info.put("userDir", System.getProperty("user.dir"));
            info.put("availableProcessors", runtime.availableProcessors());

            // ========== 应用信息 ==========
            info.put("appName", "API Server");
            info.put("appVersion", resolveAppVersion());
            info.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());
            info.put("springVersion", org.springframework.core.SpringVersion.getVersion());

            // ========== 构建时间（从 MANIFEST.MF 或当前时间作为兜底）==========
            String buildTime = "N/A";
            try {
                Package pkg = getClass().getPackage();
                if (pkg != null && pkg.getImplementationVersion() != null) {
                    buildTime = pkg.getImplementationVersion();
                }
            } catch (Exception ignored) {
            }
            info.put("buildTime", buildTime);

            // ========== 运行环境 ==========
            String[] activeProfiles = System.getProperty("spring.profiles.active", "default").split(",");
            info.put("environment", String.join(", ", activeProfiles));

            // ========== 运行时间 ==========
            long uptimeMillis = runtimeBean.getUptime();
            Duration uptime = Duration.ofMillis(uptimeMillis);
            long days = uptime.toDays();
            long hours = uptime.toHours() % 24;
            long minutes = uptime.toMinutes() % 60;
            info.put("uptimeMillis", uptimeMillis);
            info.put("uptime", String.format("%d天 %d小时 %d分钟", days, hours, minutes));
            info.put("startTime", LocalDateTime.now().minus(uptime).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // ========== 数据库信息 ==========
            // 通过 DatabaseDialectProvider 动态检测实际数据库类型
            DatabaseDialectProvider.DbType dbType = databaseDialectProvider.detectDbType();
            info.put("databaseType", dbType.name());

            // 从真实 DataSource 获取数据库产品名称和版本
            String dbProductName = null;
            String dbProductVersion = null;
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                dbProductName = meta.getDatabaseProductName();
                dbProductVersion = meta.getDatabaseProductVersion();

                // 对于 PostgreSQL，同时获取详细版本信息
                if (dbType == DatabaseDialectProvider.DbType.POSTGRESQL) {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT version()")) {
                        if (rs.next()) {
                            info.put("databaseFullVersion", rs.getString(1));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("无法获取数据库元信息: {}", e.getMessage());
            }

            info.put("databaseProductName", dbProductName != null ? dbProductName : dbType.name());
            info.put("databaseVersion", dbProductVersion != null ? dbProductVersion : "N/A");

            // ========== JVM 内存 ==========
            long heapMax = runtime.maxMemory();
            long heapTotal = runtime.totalMemory();
            long heapUsed = heapTotal - runtime.freeMemory();
            info.put("heapMaxBytes", heapMax);
            info.put("heapMaxMB", heapMax / 1024 / 1024);
            info.put("heapTotalBytes", heapTotal);
            info.put("heapTotalMB", heapTotal / 1024 / 1024);
            info.put("heapUsedBytes", heapUsed);
            info.put("heapUsedMB", heapUsed / 1024 / 1024);
            info.put("memoryUsage", heapMax > 0 ? Math.round((double) heapUsed / heapMax * 100) : 0);

            // ========== CPU 使用率 ==========
            int processorCount = runtime.availableProcessors();
            double cpuLoad = -1;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                // getCpuLoad() 返回 0.0~1.0，1.0=所有核满载=100%
                cpuLoad = sunOsBean.getCpuLoad();
                // 第一次调用可能返回 -1，等待后重试
                if (cpuLoad < 0) {
                    Thread.sleep(100);
                    cpuLoad = sunOsBean.getCpuLoad();
                }
                if (cpuLoad >= 0) {
                    cpuLoad = cpuLoad * 100;
                }
            }
            if (cpuLoad < 0 || Double.isNaN(cpuLoad)) {
                // 兜底：使用系统负载平均值
                double loadAvg = osBean.getSystemLoadAverage();
                if (loadAvg >= 0) {
                    cpuLoad = loadAvg * 100 / processorCount;
                }
            }
            // -1 表示无法获取CPU数据，前端可据此显示 "N/A"
            info.put("cpuUsage", cpuLoad >= 0 ? Math.round(cpuLoad) : -1);

            // ========== 磁盘使用率 ==========
            java.io.File root = new java.io.File(".");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            info.put("diskTotalBytes", totalSpace);
            info.put("diskTotalGB", Math.round(totalSpace * 100.0 / (1024 * 1024 * 1024)) / 100.0);
            info.put("diskFreeBytes", freeSpace);
            info.put("diskFreeGB", Math.round(freeSpace * 100.0 / (1024 * 1024 * 1024)) / 100.0);
            info.put("diskUsedBytes", usedSpace);
            info.put("diskUsedGB", Math.round(usedSpace * 100.0 / (1024 * 1024 * 1024)) / 100.0);
            info.put("diskUsage", totalSpace > 0 ? Math.round((double) usedSpace / totalSpace * 100) : 0);

            // ========== 环境变量（安全过滤） ==========
            Map<String, String> envVars = new LinkedHashMap<>();
            String[] safeEnvKeys = {
                "SPRING_PROFILES_ACTIVE", "SERVER_PORT", "JAVA_HOME",
                "TZ", "LANG", "HOME", "USER", "SHELL",
                "LOG_LEVEL", "LOG_FILE_PATH"
            };
            for (String key : safeEnvKeys) {
                String value = System.getenv(key);
                if (value != null && !value.isEmpty()) {
                    // 脱敏处理
                    if (key.contains("PASSWORD") || key.contains("SECRET") || key.contains("KEY")) {
                        value = "***";
                    }
                    envVars.put(key, value);
                }
            }
            info.put("envVars", envVars);

            log.info("系统信息查询成功");
            return ApiResponse.success(info);

        } catch (Exception e) {
            log.error("获取系统信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取系统信息失败: " + e.getMessage());
        }
    }
}
