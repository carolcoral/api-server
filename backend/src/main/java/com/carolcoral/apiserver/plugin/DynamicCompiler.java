/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 动态 Java 代码编译器
 * <p>
 * 在运行时编译用户通过页面提交的 Java 源码，生成实现了 {@link CustomResponseTransformer}
 * 接口的类实例，用于自定义 Mock 响应处理逻辑。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>源码验证</b> — 安全检查，禁止使用反射、IO、网络等危险 API</li>
 *   <li><b>动态编译</b> — 使用 JDK 内置 {@link javax.tools.JavaCompiler} 编译内存中的源码</li>
 *   <li><b>字节码捕获</b> — 通过 {@link ClassFileManager} 在内存中捕获编译产物，不落盘</li>
 *   <li><b>类加载</b> — 通过 {@link DynamicClassLoader} 加载编译后的类</li>
 *   <li><b>三级缓存</b> — 源码缓存、实例缓存、类字节码缓存，源码不变时直接复用</li>
 *   <li><b>多环境适配</b> — 支持 IDE 开发、Maven 运行、Spring Boot fat JAR 部署</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * CustomResponseTransformer transformer = DynamicCompiler.compileAndInstantiate(apiId, sourceCode);
 * MockResponseDTO result = transformer.transform(response, request, apiName, apiPath);
 * }</pre>
 *
 * @author carolcoral
 */
public class DynamicCompiler {

    private static final Logger log = LoggerFactory.getLogger(DynamicCompiler.class);

    /** 全局唯一的 Java 编译器实例，类加载时通过 {@link #initCompiler()} 初始化 */
    private static final JavaCompiler compiler = initCompiler();

    /** 源码缓存：apiId -> 上次编译的原始源码，用于判断是否需要重新编译 */
    private static final ConcurrentHashMap<Long, String> sourceCodeCache = new ConcurrentHashMap<>();
    /** 实例缓存：apiId -> 编译后的 {@link CustomResponseTransformer} 实例 */
    private static final ConcurrentHashMap<Long, CustomResponseTransformer> instanceCache = new ConcurrentHashMap<>();
    /** 类字节码缓存：apiId -> 编译后的 JVM {@link Class} 对象 */
    private static final ConcurrentHashMap<Long, Class<?>> classCache = new ConcurrentHashMap<>();

    /** 编译时 classpath，在 {@link #initCompiler()} 或 {@link #forceStaticInitialization()} 中构建 */
    private static volatile String compilationClasspath = null;

    /** 类是否已加载标志（保留字段，供将来扩展使用） */
    private static volatile boolean classLoaded = false;

    /**
     * 初始化 Java 编译器
     * <p>
     * 按以下顺序尝试获取 {@link javax.tools.JavaCompiler} 实例，
     * 任一方式成功即返回并同时构建 {@link #compilationClasspath}：
     * </p>
     * <ol>
     *   <li><b>直接获取</b> — 通过 {@link javax.tools.ToolProvider#getSystemJavaCompiler()}，
     *       适用于 JDK 环境（JDK 9+ 或完整 JDK 安装）</li>
     *   <li><b>从 tools.jar 加载</b> — 从 {@code JAVA_HOME/lib/tools.jar} 反射加载编译器，
     *       适用于旧版 JDK 8 或某些定制 JRE</li>
     *   <li><b>从进程路径推断</b> — 通过当前运行的 java 进程路径反推 JDK 目录，
     *       适用于未设置 {@code JAVA_HOME} 但运行在 JDK 下的场景</li>
     * </ol>
     * <p>
     * 如果所有方式均失败，将返回 {@code null}，后续所有编译操作都会抛出
     * {@link CompilationException}，提示用户使用 JDK 而非 JRE 运行。
     * </p>
     *
     * @return {@link javax.tools.JavaCompiler} 实例，失败返回 {@code null}
     */
    private static JavaCompiler initCompiler() {
        // Java 21+ 在 Spring Boot fat jar 环境下调用 ToolProvider 可能触发 SPI 循环加载
        // 临时切换线程上下文类加载器到系统类加载器以避免此问题
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
            return doInitCompiler();
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static JavaCompiler doInitCompiler() {
        // 方式1: 直接获取
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        if (c != null) {
            log.info("找到Java编译器（ToolProvider）");
            compilationClasspath = buildClasspath();
            log.info("动态编译器初始化成功, classpath长度: {}", compilationClasspath.length());
            return c;
        }

        // 方式2: 从当前JVM的tools.jar获取（仅JDK有效，JRE无效）
        // JDK的lib/tools.jar在Spring Boot fat JAR中被重打包到BOOT-INF/lib
        try {
            String javaHome = System.getProperty("java.home");
            File toolsJar = new File(javaHome + "/lib/tools.jar");
            if (toolsJar.exists()) {
                log.info("找到tools.jar: {}", toolsJar);
                URLClassLoader toolCl = new URLClassLoader(
                        new URL[]{toolsJar.toURI().toURL()},
                        Thread.currentThread().getContextClassLoader());
                Thread.currentThread().setContextClassLoader(toolCl);
                Class<?> toolProviderClass = toolCl.loadClass("com.sun.tools.javac.api.ToolProvider");
                Object tool = toolProviderClass.getMethod("getSystemJavaCompiler").invoke(null);
                if (tool instanceof JavaCompiler) {
                    compilationClasspath = buildClasspath();
                    log.info("动态编译器初始化成功（从tools.jar）");
                    return (JavaCompiler) tool;
                }
            }
        } catch (Exception e) {
            log.warn("从tools.jar获取编译器失败: {}", e.getMessage());
        }

        // 方式3: 从当前运行的java路径推断JDK
        try {
            String javaExe = System.getProperty("java.home") + "/bin/java";
            if (new File(javaExe).exists()) {
                File javaHome = new File(System.getProperty("java.home")).getParentFile();
                if (javaHome != null) {
                    File libDir = new File(javaHome, "lib");
                    File toolsJar = new File(libDir, "tools.jar");
                    if (toolsJar.exists()) {
                        log.info("找到tools.jar（备用路径）: {}", toolsJar);
                        URLClassLoader toolCl = new URLClassLoader(
                                new URL[]{toolsJar.toURI().toURL()},
                                Thread.currentThread().getContextClassLoader());
                        Class<?> toolProviderClass = toolCl.loadClass("com.sun.tools.javac.api.ToolProvider");
                        Object tool = toolProviderClass.getMethod("getSystemJavaCompiler").invoke(null);
                        if (tool instanceof JavaCompiler) {
                            compilationClasspath = buildClasspath();
                            log.info("动态编译器初始化成功（备用路径）");
                            return (JavaCompiler) tool;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从备用路径获取编译器失败: {}", e.getMessage());
        }

        log.error("未找到Java编译器。请确保使用JDK而非JRE运行本应用。");
        return null;
    }

    /**
     * 构建动态编译所需的 classpath
     * <p>
     * 按以下优先级顺序收集编译依赖路径，确保编译器能够解析项目中定义的类
     * 以及第三方依赖（jackson、spring、slf4j 等）：
     * </p>
     * <ol start="0">
     *   <li>{@code target/classes} — IDE/Maven 运行时的编译产物目录</li>
     *   <li>{@link URLClassLoader#getURLs()} — 从当前线程上下文 ClassLoader 链获取所有 URL</li>
     *   <li>{@code DynamicCompiler.class} 自身所在位置 — 通过 {@code ProtectionDomain} 推断</li>
     *   <li>{@code CustomResponseTransformer.class} 所在位置 — 接口定义所在位置</li>
     *   <li>{@code java.class.path} 系统属性 — JVM 启动时的完整 classpath</li>
     *   <li>Spring Boot fat JAR 扫描 — 提取 {@code BOOT-INF/classes/} 和 {@code BOOT-INF/lib/*.jar}</li>
     *   <li>{@code ~/.m2/repository} — Maven 本地仓库中的关键依赖</li>
     *   <li>项目目录探测 — {@code backend/target/classes}、{@code target/classes} 等备选路径</li>
     * </ol>
     * <p>
     * 支持多种运行环境：IDE 直接运行、Maven exec、Spring Boot fat JAR 等。
     * </p>
     *
     * @return 使用 {@link File#pathSeparator} 分隔的 classpath 字符串
     */
    private static String buildClasspath() {
        StringBuilder sb = new StringBuilder();
        Set<String> paths = new LinkedHashSet<>();

        try {
            // 0. 首先尝试获取项目自身的target/classes（最优先，IDE和Maven运行时的主要来源）
            String userDir = System.getProperty("user.dir");
            File targetClasses = new File(userDir, "target/classes");
            if (targetClasses.exists()) {
                paths.add(targetClasses.getAbsolutePath());
                log.info("添加target/classes到classpath: {}", targetClasses.getAbsolutePath());
            }

            // 1. 尝试从URLClassLoader获取classpath（IDE、Maven直接运行）
            // 注意：JDK 9+的AppClassLoader不再继承URLClassLoader，需要兼容处理
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            while (cl != null) {
                URL[] urls = null;
                if (cl instanceof URLClassLoader urlCl) {
                    urls = urlCl.getURLs();
                } else {
                    // JDK 9+: 通过反射尝试获取URLs（某些类加载器实现getURLs但未继承URLClassLoader）
                    try {
                        java.lang.reflect.Method getURLsMethod = cl.getClass().getMethod("getURLs");
                        getURLsMethod.setAccessible(true);
                        urls = (URL[]) getURLsMethod.invoke(cl);
                    } catch (Exception ignored) {
                        // 不支持，跳过此ClassLoader
                    }
                }
                if (urls != null) {
                    for (URL url : urls) {
                        String path = urlToPath(url);
                        if (path != null && !paths.contains(path)) {
                            paths.add(path);
                        }
                    }
                }
                cl = cl.getParent();
            }

            // 2. 从DynamicCompiler自身所在位置推断classpath
            URL selfUrl = DynamicCompiler.class.getProtectionDomain().getCodeSource().getLocation();
            if (selfUrl != null) {
                String path = urlToPath(selfUrl);
                if (path != null && !paths.contains(path)) {
                    paths.add(path);
                }
            }

            // 3. 从CustomResponseTransformer所在位置推断classpath
            URL pluginUrl = CustomResponseTransformer.class.getProtectionDomain().getCodeSource().getLocation();
            if (pluginUrl != null) {
                String path = urlToPath(pluginUrl);
                if (path != null && !paths.contains(path)) {
                    paths.add(path);
                }
            }

            // 4. 添加系统类路径
            String sysCp = System.getProperty("java.class.path");
            if (sysCp != null && !sysCp.isEmpty()) {
                for (String entry : sysCp.split(File.pathSeparator)) {
                    if (entry != null && !entry.trim().isEmpty()) {
                        String trimmed = entry.trim();
                        if (!paths.contains(trimmed)) {
                            paths.add(trimmed);
                        }
                    }
                }
            }

            // 5. Spring Boot fat JAR处理：提取BOOT-INF/classes和BOOT-INF/lib到临时目录
            List<String> fatJarPaths = new ArrayList<>();
            for (String path : new ArrayList<>(paths)) {
                if (path.endsWith(".jar")) {
                    fatJarPaths.add(path);
                    // 检查是否是Spring Boot fat JAR
                    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path)) {
                        if (zip.getEntry("BOOT-INF/classes/") != null || zip.getEntry("BOOT-INF/lib/") != null) {
                            log.info("检测到Spring Boot fat JAR: {}", path);
                            // 提取BOOT-INF/classes到临时目录
                            File tmpClassesDir = extractBootInfClasses(path, zip);
                            if (tmpClassesDir != null && !paths.contains(tmpClassesDir.getAbsolutePath())) {
                                paths.add(tmpClassesDir.getAbsolutePath());
                                log.info("添加BOOT-INF/classes到classpath: {}", tmpClassesDir.getAbsolutePath());
                            }
                            // 扫描并提取BOOT-INF/lib下的所有jar
                            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                            while (entries.hasMoreElements()) {
                                java.util.zip.ZipEntry entry = entries.nextElement();
                                String name = entry.getName();
                                if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                                    // 提取内部jar到临时目录
                                    File extractedJar = extractBootInfLibJar(path, zip, entry);
                                    if (extractedJar != null && !paths.contains(extractedJar.getAbsolutePath())) {
                                        paths.add(extractedJar.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("检查fat JAR失败: {}", e.getMessage());
                    }
                }
            }

            // 6. 从Maven本地仓库动态扫描依赖
            //    优先通过java.class.path中的Maven仓库路径发现依赖，
            //    辅以常用依赖列表作为兜底，确保JDK 21+非URLClassLoader环境也能覆盖
            String m2Repo = System.getProperty("user.home") + "/.m2/repository";
            File m2Dir = new File(m2Repo);
            if (m2Dir.exists() && m2Dir.canRead()) {
                // 6a. 动态发现：从classpath已有的jar中提取groupId，扫描同groupId下的所有jar
                Set<String> scannedGroupIds = new LinkedHashSet<>();
                for (String cpEntry : paths) {
                    // 匹配Maven仓库路径: .../repository/com/alibaba/fastjson/2.0.53/fastjson-2.0.53.jar
                    int repoIdx = cpEntry.replace('\\', '/').indexOf("/.m2/repository/");
                    if (repoIdx >= 0) {
                        String relativePath = cpEntry.substring(repoIdx + "/.m2/repository/".length());
                        String[] parts = relativePath.split("/");
                        // parts: {groupId segments..., artifactId, version, artifactId-version.jar}
                        if (parts.length >= 3) {
                            StringBuilder groupId = new StringBuilder();
                            for (int i = 0; i < parts.length - 2; i++) {
                                if (groupId.length() > 0) groupId.append("/");
                                groupId.append(parts[i]);
                            }
                            if (!scannedGroupIds.contains(groupId.toString())) {
                                scannedGroupIds.add(groupId.toString());
                                // 递归扫描该groupId下的所有jar
                                File groupDir = new File(m2Dir, groupId.toString());
                                findJars(groupDir, paths);
                                log.debug("动态扫描Maven依赖: groupId={}", groupId);
                            }
                        }
                    }
                }

                // 6b. 兜底：硬编码常用依赖列表（覆盖6a未扫描到但项目实际使用的依赖）
                String[][] commonDeps = {
                    // Jackson 系列
                    {"com", "fasterxml", "jackson-core"},
                    {"com", "fasterxml", "jackson-databind"},
                    {"com", "fasterxml", "jackson-annotations"},
                    // JSON 处理
                    {"com", "jayway", "json-path"},
                    {"com", "alibaba", "fastjson"},
                    // SLF4J
                    {"org", "slf4j", "slf4j-api"},
                    // Spring 核心
                    {"org", "springframework", "spring-beans"},
                    {"org", "springframework", "spring-context"},
                    {"org", "springframework", "spring-web"},
                    {"org", "springframework", "spring-webmvc"},
                    // YAML
                    {"org", "yaml", "snakeyaml"},
                    // JWT
                    {"io", "jsonwebtoken", "jjwt-api"},
                    {"io", "jsonwebtoken", "jjwt-impl"},
                    {"io", "jsonwebtoken", "jjwt-jackson"},
                };

                for (String[] dep : commonDeps) {
                    File depBase = new File(m2Repo);
                    for (int i = 0; i < dep.length - 1; i++) {
                        depBase = new File(depBase, dep[i]);
                    }
                    if (depBase.exists()) {
                        String prefix = dep[dep.length - 1];
                        File[] versions = depBase.listFiles((dir, name) -> name.startsWith(prefix));
                        if (versions != null && versions.length > 0) {
                            Arrays.sort(versions, (a, b) -> b.getName().compareTo(a.getName()));
                            File latestVersion = versions[0];
                            // 查找jar文件（fastjson等使用 artifactId.jar 命名）
                            File jarFile = new File(latestVersion, prefix + ".jar");
                            if (!jarFile.exists()) {
                                // 尝试带版本号的命名: artifactId-version.jar
                                File[] jars = latestVersion.listFiles((dir, name) ->
                                    name.startsWith(prefix) && name.endsWith(".jar"));
                                if (jars != null && jars.length > 0) {
                                    jarFile = jars[0];
                                }
                            }
                            if (jarFile.exists() && !paths.contains(jarFile.getAbsolutePath())) {
                                paths.add(jarFile.getAbsolutePath());
                            }
                        }
                    }
                }
            }

            // 7. 从当前目录和父目录查找 target/classes
            // 7a. 当前目录下的 backend/target/classes（如从项目根目录运行）
            File backendTargetClasses = new File(userDir, "backend/target/classes");
            if (backendTargetClasses.exists() && !paths.contains(backendTargetClasses.getAbsolutePath())) {
                paths.add(backendTargetClasses.getAbsolutePath());
                log.info("添加backend/target/classes到classpath: {}", backendTargetClasses.getAbsolutePath());
            }
            // 7b. 当前目录下的 target/classes（如从 backend 目录运行）
            File currentTargetClasses = new File(userDir, "target/classes");
            if (currentTargetClasses.exists() && !paths.contains(currentTargetClasses.getAbsolutePath())) {
                paths.add(currentTargetClasses.getAbsolutePath());
                log.info("添加target/classes到classpath: {}", currentTargetClasses.getAbsolutePath());
            }
            // 7c. 父目录下的 backend/target/classes
            File parentDir = new File(userDir).getParentFile();
            if (parentDir != null) {
                File parentTargetClasses = new File(parentDir, "backend/target/classes");
                if (parentTargetClasses.exists() && !paths.contains(parentTargetClasses.getAbsolutePath())) {
                    paths.add(parentTargetClasses.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            log.warn("构建classpath时出错: {}", e.getMessage(), e);
        }

        // 确保至少包含当前目录
        if (paths.isEmpty()) {
            paths.add(".");
        }

        for (String p : paths) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(p);
        }
        String cp = sb.toString();
        log.info("编译classpath总长度: {} 字符, 路径数量: {}", cp.length(), paths.size());
        log.debug("编译classpath详情: {}", cp);
        return cp;
    }

    /**
     * 将URL转换为本地文件系统路径
     * <p>
     * 支持多种URL格式：
     * <ul>
     *   <li>{@code file:/path/to/file} — 标准文件URL</li>
     *   <li>{@code jar:file:/path/to/app.jar!/} — 旧版 JAR URL</li>
     *   <li>{@code jar:nested:/path/to/app.jar/!BOOT-INF/classes/!/} — Spring Boot 3.x 嵌套JAR URL</li>
     * </ul>
     * 所有格式都会被解析为 fat JAR 文件本身的绝对路径。
     * </p>
     *
     * @param url 要转换的URL对象，可能为 {@code null}
     * @return 文件系统绝对路径，如果URL为{@code null}或路径无效则返回 {@code null}
     */
    private static String urlToPath(URL url) {
        try {
            if (url == null) return null;

            String path = url.toString();

            // 处理各种Spring Boot fat JAR URL格式
            // Spring Boot 3.x: jar:nested:/path/to/app.jar/!BOOT-INF/classes/!/
            // 旧版: jar:file:/path/to/app.jar!/BOOT-INF/classes/
            if (path.startsWith("jar:nested:")) {
                // 提取fat JAR路径：去掉 "jar:nested:" 前缀，找到第一个 "!" 之前的路径
                path = path.substring("jar:nested:".length());
                int bangIndex = path.indexOf("!");
                if (bangIndex > 0) {
                    path = path.substring(0, bangIndex);
                }
            } else if (path.startsWith("jar:file:")) {
                path = path.substring("jar:file:".length());
                int bangIndex = path.indexOf("!");
                if (bangIndex > 0) {
                    path = path.substring(0, bangIndex);
                }
            } else if (path.startsWith("jar:")) {
                path = path.substring("jar:".length());
                int bangIndex = path.indexOf("!");
                if (bangIndex > 0) {
                    path = path.substring(0, bangIndex);
                }
            } else if (path.startsWith("file:")) {
                path = path.substring("file:".length());
            }

            // URL解码（处理 %20 等编码）
            path = java.net.URLDecoder.decode(path, "UTF-8");

            // 验证路径有效
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                File file = new File(path);
                if (file.exists()) {
                    return file.getAbsolutePath();
                }
                // 如果文件存在（可能是jar文件）
                if (new File(path).isFile()) {
                    return path;
                }
                // 返回解码后的路径
                return path;
            }
        } catch (Exception e) {
            log.debug("URL转路径失败: {}, 错误: {}", url, e.getMessage());
        }
        return null;
    }

    private static void findJars(File dir, Set<String> paths) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findJars(f, paths);
            } else if (f.getName().endsWith(".jar")) {
                paths.add(f.getAbsolutePath());
            }
        }
    }

    /**
     * 安全校验：禁止使用的危险类/包
     * <p>
     * 防止用户在自定义代码中执行反射、进程操作、文件 IO、网络访问、
     * 线程控制、类加载、脚本执行、数据库访问等潜在危险操作。
     * 每个 Pattern 用于在源码中进行正则匹配检测。
     * </p>
     */
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("java\\.lang\\.reflect"),
            Pattern.compile("java\\.lang\\.Process"),
            Pattern.compile("java\\.lang\\.Runtime"),
            // java.lang.System: 允许 currentTimeMillis() 和 out.println()，禁止 exit/exec/gc/load 等危险方法
            Pattern.compile("java\\.lang\\.System\\.(exit|gc|exec|load|loadLibrary|setSecurityManager|setErr|setIn|setOut)"),
            Pattern.compile("java\\.lang\\.Thread"),
            Pattern.compile("java\\.lang\\.ClassLoader"),
            Pattern.compile("java\\.lang\\.Class\\b"),
            Pattern.compile("java\\.io\\.File"),
            Pattern.compile("java\\.io\\.RandomAccessFile"),
            // java.net: 允许 java.net.http (HttpClient) 和 java.net.URI，禁止 Socket/URL/ServerSocket 等
            Pattern.compile("java\\.net\\.(?!http\\.|URI\\b)"),
            Pattern.compile("java\\.nio\\.file\\."),
            Pattern.compile("javax\\.script\\."),
            Pattern.compile("java\\.util\\.concurrent\\.(?!stream\\.)"),
            Pattern.compile("java\\.sql\\."),
            Pattern.compile("javax\\.sql\\."),
            Pattern.compile("com\\.carolcoral\\.apiserver\\.repository\\."),
            Pattern.compile("org\\.springframework\\.beans\\.factory\\.annotation\\.Autowired"),
            Pattern.compile("org\\.springframework\\.context\\."),
            Pattern.compile("org\\.springframework\\.jdbc\\."),
            Pattern.compile("org\\.hibernate\\."),
            Pattern.compile("jakarta\\.persistence\\.")
    );

    /**
     * 编译并实例化用户提交的自定义响应处理源码
     * <p>
     * 完整流程：
     * <ol>
     *   <li>检查编译器可用性（JDK vs JRE）</li>
     *   <li>检查源码缓存 — 相同源码直接返回已编译的实例，避免重复编译</li>
     *   <li>安全校验 — 禁止使用反射、IO、网络等危险 API</li>
     *   <li>生成唯一类名 — {@code DynamicTransformer_{apiId}_{random}}</li>
     *   <li>构建完整源码 — 添加 package、import、替换类名</li>
     *   <li>编译源码 — 使用 {@link javax.tools.JavaCompiler} 编译</li>
     *   <li>验证接口实现 — 确保编译产物实现了 {@link CustomResponseTransformer}</li>
     *   <li>实例化并缓存 — 反射创建实例，存入三级缓存</li>
     * </ol>
     * </p>
     *
     * @param apiId      接口ID，用于缓存 key 和类名生成
     * @param sourceCode 用户提交的 Java 源码，必须包含一个实现了 {@code CustomResponseTransformer} 的 public 类
     * @return 编译并实例化后的 {@link CustomResponseTransformer} 实例
     * @throws CompilationException 编译器不可用、源码验证失败、编译错误或未实现接口时抛出
     */
    public static synchronized CustomResponseTransformer compileAndInstantiate(Long apiId, String sourceCode)
            throws CompilationException {
        if (compiler == null) {
            throw new CompilationException("未找到Java编译器，请使用JDK运行");
        }

        // 如果源码没变，直接返回缓存实例
        String cachedSource = sourceCodeCache.get(apiId);
        if (cachedSource != null && cachedSource.equals(sourceCode)) {
            CustomResponseTransformer cached = instanceCache.get(apiId);
            if (cached != null) {
                log.info("使用缓存的动态转换器实例: apiId={}", apiId);
                return cached;
            }
        }

        // 安全校验
        validateSourceCode(sourceCode);

        // 生成唯一的类名
        String className = "DynamicTransformer_" + apiId + "_" + Math.abs(new SecureRandom().nextLong() % 100000);

        // 构建完整的Java源码
        String fullSourceCode = buildFullSourceCode(sourceCode, className);

        // 编译
        Class<?> compiledClass = compile(className, fullSourceCode);

        // 验证实现了接口
        if (!CustomResponseTransformer.class.isAssignableFrom(compiledClass)) {
            throw new CompilationException("代码中的类必须实现 CustomResponseTransformer 接口");
        }

        // 实例化
        CustomResponseTransformer instance;
        try {
            instance = (CustomResponseTransformer) compiledClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new CompilationException("无法实例化类: " + e.getMessage());
        }

        // 缓存
        sourceCodeCache.put(apiId, sourceCode);
        instanceCache.put(apiId, instance);
        classCache.put(apiId, compiledClass);

        log.info("动态编译成功: apiId={}, className={}", apiId, className);
        return instance;
    }

    /**
     * 验证源码安全性
     * <p>
     * 在校验阶段执行以下检查，全部通过才允许进入编译流程：
     * </p>
     * <ol>
     *   <li><b>非空检查</b> — 源码不能为 {@code null} 或空字符串</li>
     *   <li><b>类定义检查</b> — 必须包含 {@code public class} 声明</li>
     *   <li><b>接口检查</b> — 必须引用 {@code CustomResponseTransformer}</li>
     *   <li><b>安全扫描</b> — 禁止使用 {@link #FORBIDDEN_PATTERNS} 中定义的危险包/类</li>
     *   <li><b>长度限制</b> — 源码不得超过 50000 字符</li>
     * </ol>
     *
     * @param sourceCode 待验证的 Java 源码
     * @throws CompilationException 任何一项检查不通过时抛出
     */
    private static void validateSourceCode(String sourceCode) throws CompilationException {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new CompilationException("源码不能为空");
        }

        // 检查是否包含public class定义
        if (!Pattern.compile("public\\s+class\\s+\\w+").matcher(sourceCode).find()) {
            throw new CompilationException("源码中必须包含一个 public class 定义");
        }

        // 检查是否实现了接口
        if (!sourceCode.contains("CustomResponseTransformer")) {
            throw new CompilationException("类必须实现 CustomResponseTransformer 接口");
        }

        // 安全扫描：检查是否使用了禁止的类/包
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(sourceCode).find()) {
                throw new CompilationException("安全限制：代码中不允许使用 " + pattern.pattern());
            }
        }

        // 检查代码长度限制
        if (sourceCode.length() > 50000) {
            throw new CompilationException("代码长度超过限制（最大50000字符）");
        }
    }

    /**
     * 构建完整的Java源码
     * <p>
     * 将用户提交的源码包装为完整的 Java 编译单元，执行以下操作：
     * <ol>
     *   <li>添加固定的包声明 {@code com.carolcoral.apiserver.plugin.dynamic}</li>
     *   <li>提取并保留用户原有的 import 语句</li>
     *   <li>自动注入必需的 import（{@code CustomResponseTransformer}、{@code MockResponseDTO}、
     *       {@code MockRequest}、{@code java.util.*}），避免重复添加</li>
     *   <li>将用户类名替换为动态生成的唯一类名，防止多次编译产生类名冲突</li>
     * </ol>
     * </p>
     *
     * @param userCode  用户提交的原始 Java 源码
     * @param className 动态生成的唯一类名，格式为 {@code DynamicTransformer_{apiId}_{random}}
     * @return 包含完整 package、import 和类定义的 Java 源码字符串
     */
    private static String buildFullSourceCode(String userCode, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.carolcoral.apiserver.plugin.dynamic;\n\n");

        // 提取用户代码中的import语句
        String imports = extractImports(userCode);
        sb.append(imports);

        // 注入用户代码（替换类名为动态生成的类名）
        String processedCode = userCode;
        java.util.regex.Matcher classMatcher = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(userCode);
        if (classMatcher.find()) {
            String originalClassName = classMatcher.group(1);
            processedCode = userCode.replaceAll("\\b" + originalClassName + "\\b", className);
        }

        sb.append(processedCode);

        return sb.toString();
    }

    /**
     * 提取用户代码中的 import 语句并注入必需的 import
     * <p>
     * 从用户源码中提取所有 {@code import ...} 行，并自动补全编译器必需的导入：
     * <ul>
     *   <li>{@code com.carolcoral.apiserver.plugin.CustomResponseTransformer} — 接口定义</li>
     *   <li>{@code com.carolcoral.apiserver.dto.MockResponseDTO} — 响应数据传输对象</li>
     *   <li>{@code com.carolcoral.apiserver.dto.MockRequest} — 请求数据传输对象</li>
     *   <li>{@code java.util.*} — 常用工具类</li>
     * </ul>
     * 已存在的 import 不会被重复添加。
     * </p>
     *
     * @param code 用户提交的原始 Java 源码
     * @return 完整的 import 语句块（含换行符），末尾追加空行与后续代码分隔
     */
    private static String extractImports(String code) {
        StringBuilder imports = new StringBuilder();
        String[] lines = code.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                imports.append(line).append("\n");
            }
        }
        // 添加必需的import（避免重复）
        if (!imports.toString().contains("com.carolcoral.apiserver.plugin.CustomResponseTransformer")) {
            imports.append("import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n");
        }
        if (!imports.toString().contains("com.carolcoral.apiserver.dto.MockResponseDTO")) {
            imports.append("import com.carolcoral.apiserver.dto.MockResponseDTO;\n");
        }
        if (!imports.toString().contains("com.carolcoral.apiserver.dto.MockRequest")) {
            imports.append("import com.carolcoral.apiserver.dto.MockRequest;\n");
        }
        if (!imports.toString().contains("java.util.")) {
            imports.append("import java.util.*;\n");
        }
        imports.append("\n");
        return imports.toString();
    }

    /**
     * 使用 {@link javax.tools.JavaCompiler} 编译 Java 源码
     * <p>
     * 编译时会将 {@link #compilationClasspath} 作为 {@code -classpath} 选项传入，
     * 确保编译器能解析项目类（{@code MockResponseDTO} 等）和第三方依赖。
     * 编译产物通过自定义的 {@link ClassFileManager} 在内存中捕获字节码，
     * 然后使用 {@link DynamicClassLoader} 加载为 JVM 类。
     * </p>
     *
     * @param className  类的全限定名
     * @param sourceCode 完整的 Java 源码（含 package 和 import）
     * @return 编译并加载后的 {@link Class} 对象
     * @throws CompilationException 编译错误、字节码丢失或类加载失败时抛出
     */
    private static Class<?> compile(String className, String sourceCode) throws CompilationException {
        // 每次编译创建新的ClassFileManager和DiagnosticCollector
        ClassFileManager classFileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject javaFile = new JavaSourceFromString(className, sourceCode);

        // 准备编译选项，包含classpath
        List<String> options = new ArrayList<>();
        options.add("-classpath");
        options.add(compilationClasspath);

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,                      // Writer for diagnostics
                classFileManager,           // FileManager
                diagnostics,               // Diagnostics
                options,                   // Options
                null,                      // Classes to annotate (annotations)
                Collections.singletonList(javaFile) // Source files
        );

        boolean success = task.call();
        if (!success) {
            StringBuilder errorMsg = new StringBuilder();
            boolean hasError = false;
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    hasError = true;
                    long lineNum = diagnostic.getLineNumber();
                    String msg = diagnostic.getMessage(Locale.getDefault());
                    // 清理重复的路径信息
                    msg = msg.replaceAll(" location:.*", "");
                    errorMsg.append(String.format(" 行%d: %s\n", lineNum, msg));
                }
            }
            if (!hasError) {
                // 只有警告没有错误，仍然视为失败，给出友好提示
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    long lineNum = diagnostic.getLineNumber();
                    String msg = diagnostic.getMessage(Locale.getDefault());
                    msg = msg.replaceAll(" location:.*", "");
                    errorMsg.append(String.format(" 行%d: %s\n", lineNum, msg));
                }
            }
            if (errorMsg.length() > 0) {
                throw new CompilationException("编译失败:\n" + errorMsg.toString());
            }
        }

        // 加载编译后的类
        try {
            byte[] classBytes = classFileManager.getClassBytes(className);
            if (classBytes == null) {
                // 尝试多种可能的类名格式
                String[] possibleNames = {
                        className,
                        "com.carolcoral.apiserver.plugin.dynamic." + className
                };
                for (String name : possibleNames) {
                    classBytes = classFileManager.getClassBytes(name);
                    if (classBytes != null) {
                        className = name;
                        break;
                    }
                }
                if (classBytes == null) {
                    throw new CompilationException("未找到编译后的类文件，可能是编译失败但未报告错误");
                }
            }

            DynamicClassLoader classLoader = new DynamicClassLoader(
                    DynamicCompiler.class.getClassLoader());
            return classLoader.defineClass(
                    "com.carolcoral.apiserver.plugin.dynamic." + className, classBytes);
        } catch (CompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompilationException("加载编译后的类失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定接口已缓存的转换器实例
     * <p>
     * 从 {@link #instanceCache} 中查询，不会触发编译。返回 {@code null} 表示
     * 该接口没有已编译的实例（可能需要重新编译）。
     * </p>
     *
     * @param apiId 接口ID
     * @return 缓存的 {@link CustomResponseTransformer} 实例，未缓存时返回 {@code null}
     */
    public static CustomResponseTransformer getCachedInstance(Long apiId) {
        return instanceCache.get(apiId);
    }

    /**
     * 清除指定接口的全部缓存
     * <p>
     * 同时清除三级缓存（源码缓存、实例缓存、类字节码缓存），
     * 通常在接口的 {@code customResponseSource} 被修改或删除时调用。
     * </p>
     *
     * @param apiId 接口ID
     */
    public static void evictCache(Long apiId) {
        sourceCodeCache.remove(apiId);
        instanceCache.remove(apiId);
        classCache.remove(apiId);
        log.info("清除动态转换器缓存: apiId={}", apiId);
    }

    /**
     * 清除所有动态编译缓存
     * <p>
     * 清空三级缓存（源码缓存、实例缓存、类字节码缓存），
     * 通常在系统配置中将自定义响应缓存时间设为 0 时调用，
     * 确保所有后续请求都重新编译并执行最新代码。
     * </p>
     */
    public static void clearAllCaches() {
        int size = sourceCodeCache.size();
        sourceCodeCache.clear();
        instanceCache.clear();
        classCache.clear();
        log.info("已清除全部动态编译缓存，共 {} 条", size);
    }

    /**
     * 检查指定接口是否有已缓存的源码
     * <p>
     * 用于判断是否需要重新编译：如果源码未缓存（或源码已变更），
     * 需要调用 {@link #compileAndInstantiate(Long, String)} 进行编译。
     * </p>
     *
     * @param apiId 接口ID
     * @return {@code true} 如果存在缓存的源码
     */
    public static boolean hasCachedSource(Long apiId) {
        return sourceCodeCache.containsKey(apiId);
    }

    /**
     * 获取指定接口已缓存的源码
     * <p>
     * 返回上次编译时使用的原始源码字符串，用于与当前源码比对判断是否需要重新编译。
     * </p>
     *
     * @param apiId 接口ID
     * @return 缓存的源码字符串，未缓存时返回 {@code null}
     */
    public static String getCachedSource(Long apiId) {
        return sourceCodeCache.get(apiId);
    }

    /**
     * 内存中的 Java 源文件对象
     * <p>
     * 继承 {@link SimpleJavaFileObject}，将内存中的字符串作为 Java 源码提供给编译器。
     * 文件名由类名决定，URI 使用 {@code string:///} 协议。
     * </p>
     */
    private static class JavaSourceFromString extends SimpleJavaFileObject {
        /** 源码内容 */
        private final String code;

        /**
         * @param name 类名（不含包名）
         * @param code Java 源码字符串
         */
        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * 自定义 ClassLoader，用于加载动态编译后的类字节码
     * <p>
     * 通过 {@link #defineClass(String, byte[])} 将内存中的字节码定义为 JVM 类，
     * 使编译产物可以被实例化和调用。
     * </p>
     */
    private static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }

        /**
         * 将字节数组定义为 JVM 类
         *
         * @param name  类的全限定名
         * @param bytes 类字节码
         * @return 已定义的 {@link Class} 对象
         */
        public Class<?> defineClass(String name, byte[] bytes) {
            return super.defineClass(name, bytes, 0, bytes.length);
        }
    }

    /**
     * 自定义 FileManager，在内存中捕获编译后的字节码
     * <p>
     * 重写 {@link #getJavaFileForOutput} 方法，将编译器输出的 {@code .class} 文件
     * 重定向到内存中的 {@link ByteArrayOutputStream}，避免写入磁盘。
     * 支持多种类名匹配方式（精确匹配、后缀匹配、下划线变体）以兼容不同编译环境。
     * </p>
     */
    private static class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        /** 类名 -> 字节码输出流 */
        private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();

        ClassFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                classBytes.put(className, bos);
                return new SimpleJavaFileObject(
                        URI.create("bytes:///" + className.replace('.', '/') + ".class"),
                        JavaFileObject.Kind.CLASS) {
                    @Override
                    public OutputStream openOutputStream() {
                        return bos;
                    }
                };
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        /**
         * 获取编译后的类字节码
         *
         * @param className 类全限定名
         * @return 类字节码数组，未找到时返回 {@code null}
         */
        byte[] getClassBytes(String className) {
            // 先精确匹配
            if (classBytes.containsKey(className)) {
                return classBytes.get(className).toByteArray();
            }
            // 再尝试带包名的匹配
            for (Map.Entry<String, ByteArrayOutputStream> entry : classBytes.entrySet()) {
                if (entry.getKey().equals(className) ||
                    entry.getKey().endsWith("." + className) ||
                    entry.getKey().equals(className.replace("-", "_"))) {
                    return entry.getValue().toByteArray();
                }
            }
            return null;
        }
    }

    /**
     * 动态编译异常
     * <p>
     * 用于包装编译过程中的各种错误，包括编译错误、安全校验失败、
     * 类加载失败等。上层调用者可通过 {@link #getMessage()} 获取详细的错误描述。
     * </p>
     */
    public static class CompilationException extends Exception {
        /**
         * @param message 错误描述信息
         */
        public CompilationException(String message) {
            super(message);
        }
    }

    /**
     * 检查编译器是否可用
     * <p>
     * 调用此方法会触发类的静态初始化，确保 {@link #compiler} 字段已完成赋值。
     * 在上层代码中用于判断是否显示"动态响应处理器"相关功能。
     * </p>
     *
     * @return {@code true} 如果 Java 编译器可用（JDK 环境），{@code false} 如果是 JRE 环境
     */
    public static boolean isCompilerAvailable() {
        // 强制触发静态初始化
        forceStaticInitialization();
        return compiler != null;
    }

    /**
     * 获取编译时的 classpath 字符串
     * <p>
     * 调用此方法会触发类的静态初始化。返回的 classpath 使用系统路径分隔符
     * （{@link File#pathSeparator}）连接多个路径，包含项目类目录、依赖 jar 等。
     * 主要用于调试和日志输出。
     * </p>
     *
     * @return 编译 classpath 字符串，可能为空字符串（如果 classpath 构建失败）
     */
    public static String getCompilationClasspath() {
        // 强制触发静态初始化
        forceStaticInitialization();
        return compilationClasspath;
    }

    /**
     * 强制触发类的静态初始化
     * <p>
     * 由于 {@code compiler} 字段的初始化在类加载时完成，但 {@code compilationClasspath}
     * 可能由于初始化顺序问题未被正确设置。此方法通过检查并重新构建 classpath
     * 来确保两者都已就绪。
     * </p>
     */
    private static void forceStaticInitialization() {
        // 访问 compiler 会触发类的静态初始化
        // compiler 是 final 的，所以在任何线程看到它之前，它已经被初始化
        // 但我们需要确保 compilationClasspath 也被设置
        if (compilationClasspath == null) {
            // 重新构建 classpath
            compilationClasspath = buildClasspath();
            log.info("动态编译classpath已重新构建: 长度={}", compilationClasspath.length());
        }
    }

    // 临时目录缓存，避免重复提取
    private static final Map<String, File> extractedClassesDirs = new ConcurrentHashMap<>();
    private static final Map<String, File> extractedJars = new ConcurrentHashMap<>();

    /**
     * 获取已提取的 JAR 缓存 Map
     * <p>
     * 由于 {@link #extractedJars} 是静态 final 字段，在类加载时初始化，
     * 但某些环境（如麒麟服务器上的特定 JVM）中可能存在初始化顺序问题。
     * 此方法提供安全访问，如果 Map 为 null 则创建新的 ConcurrentHashMap。
     * </p>
     */
    private static Map<String, File> getExtractedJars() {
        if (extractedJars == null) {
            return new ConcurrentHashMap<>();
        }
        return extractedJars;
    }

    /**
     * 获取已提取的 classes 目录缓存 Map
     */
    private static Map<String, File> getExtractedClassesDirs() {
        if (extractedClassesDirs == null) {
            return new ConcurrentHashMap<>();
        }
        return extractedClassesDirs;
    }

    /**
     * 从Spring Boot fat JAR中提取BOOT-INF/classes到临时目录
     * <p>
     * 当应用以 {@code java -jar} 方式运行时，项目自身的类文件（包括
     * {@code MockResponseDTO}、{@code MockRequest}、{@code CustomResponseTransformer} 等）
     * 被打包在 fat JAR 的 {@code BOOT-INF/classes/} 路径下。
     * 动态编译器无法直接读取 JAR 内部的类文件，因此需要将其提取到临时目录中。
     * </p>
     * <p>
     * 为提高性能，只提取 {@code com/carolcoral/} 包下的项目类文件，
     * 不提取第三方依赖的类。提取结果会被缓存，同一 fat JAR 不会重复提取。
     * </p>
     *
     * @param fatJarPath Spring Boot fat JAR 文件的绝对路径
     * @param zip        已打开的 fat JAR 的 {@link java.util.zip.ZipFile} 对象
     * @return 提取后的临时目录路径，如果提取失败或没有可提取的类则返回 {@code null}
     */
    private static File extractBootInfClasses(String fatJarPath, java.util.zip.ZipFile zip) {
        try {
            // 检查缓存（使用安全 getter）
            File cached = getExtractedClassesDirs().get(fatJarPath);
            if (cached != null && cached.exists()) {
                return cached;
            }

            // 创建临时目录
            File tmpDir = new File(System.getProperty("java.io.tmpdir"),
                    "api-server-classes-" + fatJarPath.hashCode());
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }

            // 只提取项目自己的类文件（com/carolcoral/...），不需要全部提取
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            int extractedCount = 0;
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // 只提取BOOT-INF/classes下的项目类
                if (name.startsWith("BOOT-INF/classes/com/carolcoral/") && !entry.isDirectory()) {
                    String relativePath = name.substring("BOOT-INF/classes/".length());
                    File targetFile = new File(tmpDir, relativePath);
                    File parentDir = targetFile.getParentFile();
                    if (!parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    extractedCount++;
                }
            }
            if (extractedCount > 0) {
                log.info("从fat JAR提取了 {} 个项目类文件到: {}", extractedCount, tmpDir.getAbsolutePath());
                getExtractedClassesDirs().put(fatJarPath, tmpDir);
                return tmpDir;
            }
        } catch (Exception e) {
            log.warn("提取BOOT-INF/classes失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从Spring Boot fat JAR中提取BOOT-INF/lib下的内部jar到临时目录
     * <p>
     * Spring Boot fat JAR 将第三方依赖打包在 {@code BOOT-INF/lib/} 路径下作为嵌套 JAR。
     * 动态编译器需要这些依赖（如 jackson、spring 等）才能正常编译用户的自定义代码。
     * 此方法将指定的嵌套 JAR 提取到系统临时目录，并缓存已提取的结果。
     * </p>
     * <p>
     * 提取策略：
     * <ul>
     *   <li>优先检查缓存，避免重复提取</li>
     *   <li>如果临时文件已存在且大小与 JAR 条目一致，直接复用</li>
     *   <li>否则重新从 fat JAR 中读取并写入临时文件</li>
     * </ul>
     * </p>
     *
     * @param fatJarPath Spring Boot fat JAR 文件的绝对路径，用于构造缓存 key
     * @param zip        已打开的 fat JAR 的 {@link java.util.zip.ZipFile} 对象
     * @param jarEntry   {@code BOOT-INF/lib/} 下的嵌套 JAR 条目
     * @return 提取后的临时 jar 文件路径，如果提取失败则返回 {@code null}
     */
    private static File extractBootInfLibJar(String fatJarPath, java.util.zip.ZipFile zip,
                                              java.util.zip.ZipEntry jarEntry) {
        try {
            String jarName = jarEntry.getName().substring("BOOT-INF/lib/".length());
            String cacheKey = fatJarPath + "::" + jarName;
            Map<String, File> jarCache = getExtractedJars();

            // 检查缓存
            File cached = jarCache.get(cacheKey);
            if (cached != null && cached.exists()) {
                return cached;
            }

            // 创建临时文件
            File tmpJar = new File(System.getProperty("java.io.tmpdir"), jarName);
            // 如果文件已存在且大小匹配，直接复用
            if (tmpJar.exists() && tmpJar.length() == jarEntry.getSize()) {
                jarCache.put(cacheKey, tmpJar);
                return tmpJar;
            }

            // 提取jar
            try (java.io.InputStream is = zip.getInputStream(jarEntry);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpJar)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }

            if (tmpJar.exists() && tmpJar.length() > 0) {
                jarCache.put(cacheKey, tmpJar);
                log.debug("从fat JAR提取依赖jar: {} -> {}", jarName, tmpJar.getAbsolutePath());
                return tmpJar;
            }
        } catch (Exception e) {
            log.warn("提取内部jar失败: {} -> {}", jarEntry.getName(), e.getMessage());
        }
        return null;
    }
}
