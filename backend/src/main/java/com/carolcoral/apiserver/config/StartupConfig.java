/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import com.carolcoral.apiserver.entity.CustomCodeTemplate;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.SystemConfig;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.plugin.DynamicCompiler;
import com.carolcoral.apiserver.repository.CustomCodeTemplateRepository;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.repository.SystemConfigRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.util.CacheUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 启动配置类 - 初始化数据
 *
 * @author carolcoral
 */
@Tag(name = "启动配置", description = "应用启动初始化配置")
@Component
public class StartupConfig implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartupConfig.class);

    /**
     * 构造器
     */
    public StartupConfig(UserRepository userRepository,
        ProjectRepository projectRepository,
        MockApiRepository mockApiRepository,
        MockResponseRepository mockResponseRepository,
        PasswordEncoder passwordEncoder,
        CacheUtil cacheUtil,
        SystemConfigRepository systemConfigRepository,
        CustomCodeTemplateRepository customCodeTemplateRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.mockResponseRepository = mockResponseRepository;
        this.passwordEncoder = passwordEncoder;
        this.cacheUtil = cacheUtil;
        this.systemConfigRepository = systemConfigRepository;
        this.customCodeTemplateRepository = customCodeTemplateRepository;
    }

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;
    private final MockResponseRepository mockResponseRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheUtil cacheUtil;
    private final SystemConfigRepository systemConfigRepository;
    private final CustomCodeTemplateRepository customCodeTemplateRepository;

    // 从系统属性/环境变量获取管理员配置（优先环境变量，兼容 -D 系统属性）
    // 优先级：-D 系统属性 > 环境变量 > 代码默认值
    private String adminUsername = getConfigValue("ADMIN_USERNAME", "admin");
    private String adminPassword = getConfigValue("ADMIN_PASSWORD", "!CHANGE_ME!");
    private String adminEmail = getConfigValue("ADMIN_EMAIL", "admin@example.com");

    /**
     * 获取配置值，优先级：System.getProperty > System.getenv > 默认值
     */
    private static String getConfigValue(String key, String defaultValue) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.isEmpty()) {
            return prop;
        }
        String env = System.getenv(key);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return defaultValue;
    }

    @Override
    @Operation(summary = "初始化数据", description = "应用启动时初始化管理员账号和示例数据")
    public void run(String... args) throws Exception {
        log.info("开始初始化应用数据...");

        try {
            // 检查密码是否配置
            if (adminPassword == null || adminPassword.isEmpty()) {
                log.warn("管理员密码未配置，跳过创建管理员账号。请在环境变量中设置ADMIN_PASSWORD");
                return;
            }

            // 验证密码强度
            if (!isStrongPassword(adminPassword)) {
                log.warn("管理员密码强度不足，跳过创建管理员账号。密码必须包含大小写字母、数字和特殊字符，且长度至少8位");
                return;
            }

            // 初始化管理员账号
            initAdminUser();

            // 初始化示例项目
            initExampleProject();

            // 预编译已存储的自定义响应处理器代码
            precompileCustomTransformers();

            // 初始化系统配置默认值
            initSystemConfigDefaults();

            // 初始化系统默认代码模板
            initSystemCodeTemplates();

            log.info("应用数据初始化完成");
        } catch (Exception e) {
            log.error("应用数据初始化失败");
        }
    }

    /**
     * 检查密码强度
     */
    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[@$!%*?&].*");
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    /**
     * 初始化管理员账号
     */
    @Operation(summary = "初始化管理员账号")
    private void initAdminUser() {
        try {
            // 检查管理员账号是否已存在（使用findByUsername代替existsByUsername避免SQLite兼容性问题）
            Optional<User> existingAdmin = userRepository.findByUsername(adminUsername);
            if (existingAdmin.isPresent()) {
                // 管理员已存在，同步更新密码和邮箱（支持密码热更新）
                User admin = existingAdmin.get();
                String newEncodedPassword = passwordEncoder.encode(adminPassword);
                boolean needUpdate = false;

                if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                    admin.setPassword(newEncodedPassword);
                    needUpdate = true;
                    log.info("检测到管理员密码已变更，正在同步更新...");
                }

                if (adminEmail != null && !adminEmail.isEmpty()
                        && (admin.getEmail() == null || !adminEmail.equals(admin.getEmail()))) {
                    admin.setEmail(adminEmail);
                    needUpdate = true;
                }

                if (needUpdate) {
                    admin.setUpdateTime(LocalDateTime.now());
                    userRepository.save(admin);
                    log.info("管理员账号已同步更新: {}", adminUsername);
                } else {
                    log.info("管理员账号已存在且配置一致: {}", adminUsername);
                }
                return;
            }

            // 创建管理员账号
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEmail(adminEmail);
            admin.setRole(User.UserRole.ADMIN);
            admin.setEnabled(true);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());

            userRepository.save(admin);
            log.info("初始化管理员账号成功: {}", adminUsername);

        } catch (Exception e) {
            log.error("初始化管理员账号失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化示例项目
     * <p>
     * 仅在数据库无任何项目数据时（首次启动）创建默认测试项目。
     * 如果数据库已有项目（包括升级后用户已删除 test 项目的情况），则跳过创建，
     * 避免每次升级都重建已删除的系统默认测试项目。
     * </p>
     */
    @Operation(summary = "初始化示例项目")
    private void initExampleProject() {
        try {
            // 检查数据库中是否已有任何项目，有则说明非首次启动，跳过示例数据创建
            if (projectRepository.count() > 0) {
                log.info("数据库已有项目数据，跳过测试项目创建（项目总数: {}）", projectRepository.count());
                return;
            }

            String projectCode = "test";

            // 获取管理员用户
            User admin = userRepository.findByUsername(adminUsername).orElse(null);
            if (admin == null) {
                log.error("管理员用户不存在，无法创建test项目");
                return;
            }

            // 创建test项目
            Project project = new Project();
            project.setName("测试项目");
            project.setCode(projectCode);
            project.setDescription("这是一个测试项目，包含一些常用的Mock接口");
            project.setEnabled(true);
            project.setCreateUserId(admin.getId());
            project.setCreateTime(LocalDateTime.now());
            project.setUpdateTime(LocalDateTime.now());

            Project savedProject = projectRepository.save(project);
            log.info("创建test项目成功: {}", projectCode);

            // 创建示例接口
            initExampleApis(savedProject, admin.getId());

        } catch (Exception e) {
            log.error("初始化test项目失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化示例接口
     *
     * @param project  项目
     * @param userId   用户ID
     */
    @Operation(summary = "初始化示例接口")
    private void initExampleApis(Project project, Long userId) {
        try {
            // 创建登录接口
            MockApi loginApi = createMockApi(project, "登录接口", "/login", MockApi.HttpMethod.POST, userId);
            createMockResponse(loginApi, 200, "{\"code\": 200, \"message\": \"登录成功\", \"data\": {\"token\": \"test-token-123\", \"userId\": 1, \"username\": \"admin\"}}", userId, true);
            createMockResponse(loginApi, 401, "{\"code\": 401, \"message\": \"用户名或密码错误\"}", userId, false);

            // 创建用户登录接口
            MockApi userInfoApi = createMockApi(project, "获取用户信息", "/user/info", MockApi.HttpMethod.GET, userId);
            createMockResponse(userInfoApi, 200, "{\"code\": 200, \"message\": \"成功\", \"data\": {\"userId\": 1, \"username\": \"admin\", \"email\": \"admin@example.com\", \"role\": \"ADMIN\"}}", userId, true);

            // 创建商品列表接口
            MockApi productListApi = createMockApi(project, "商品列表", "/products", MockApi.HttpMethod.GET, userId);
            createMockResponse(productListApi, 200, "{\"code\": 200, \"message\": \"成功\", \"data\": [{\"id\": 1, \"name\": \"iPhone 15\", \"price\": 5999}, {\"id\": 2, \"name\": \"MacBook Pro\", \"price\": 12999}]}", userId, true);

            // 创建订单创建接口（带条件响应）
            MockApi orderApi = createMockApi(project, "创建订单", "/order/create", MockApi.HttpMethod.POST, userId);
            MockResponse successResponse = createMockResponse(orderApi, 200, "{\"code\": 200, \"message\": \"订单创建成功\", \"data\": {\"orderId\": \"2024001\", \"amount\": 99.99}}", userId, true);
            successResponse.setCondition("$.productId == '1'");
            successResponse.setConditionDesc("当商品ID为1时返回成功");
            mockResponseRepository.save(successResponse);

            MockResponse errorResponse = createMockResponse(orderApi, 400, "{\"code\": 400, \"message\": \"商品库存不足\"}", userId, false);
            errorResponse.setCondition("$.productId == '999'");
            errorResponse.setConditionDesc("当商品ID为999时返回库存不足");
            mockResponseRepository.save(errorResponse);

            // 创建随机响应接口
            MockApi randomApi = createMockApi(project, "随机响应接口", "/random", MockApi.HttpMethod.GET, userId);
            randomApi.setEnableRandom(true);
            mockApiRepository.save(randomApi);

            // 随机响应接口需要激活两个响应
            createMockResponse(randomApi, 200, "{\"code\": 200, \"message\": \"成功（高概率）\", \"data\": \"这是200响应\"}", 80, userId, true);
            createMockResponse(randomApi, 500, "{\"code\": 500, \"message\": \"服务器错误（低概率）\", \"data\": \"这是500响应\"}", 20, userId, true);

            // 初始化缓存
            cacheUtil.initCache();

            log.info("创建示例接口成功，共 {} 个接口", 5);

        } catch (Exception e) {
            log.error("初始化示例接口失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建Mock接口
     *
     * @param project     项目
     * @param name        接口名称
     * @param path        接口路径
     * @param method      请求方法
     * @param userId      用户ID
     * @return Mock接口
     */
    private MockApi createMockApi(Project project, String name, String path, MockApi.HttpMethod method, Long userId) {
        MockApi api = new MockApi();
        api.setName(name);
        api.setPath(path);
        api.setMethod(method);
        api.setRequestType(MockApi.RequestType.HTTP);
        api.setProject(project);
        api.setEnabled(true);
        api.setEnableRandom(false);
        api.setCreateUserId(userId);
        api.setCreateTime(LocalDateTime.now());
        api.setUpdateTime(LocalDateTime.now());

        return mockApiRepository.save(api);
    }

    /**
     * 创建Mock响应
     *
     * @param mockApi       接口
     * @param statusCode    状态码
     * @param responseBody  响应体
     * @param userId        用户ID
     * @param active        是否激活
     * @return Mock响应
     */
    private MockResponse createMockResponse(MockApi mockApi, int statusCode, String responseBody, Long userId, boolean active) {
        return createMockResponse(mockApi, statusCode, responseBody, 100, userId, active);
    }

    /**
     * 创建Mock响应（带权重）
     *
     * @param mockApi       接口
     * @param statusCode    状态码
     * @param responseBody  响应体
     * @param weight        权重
     * @param userId        用户ID
     * @param active        是否激活
     * @return Mock响应
     */
    private MockResponse createMockResponse(MockApi mockApi, int statusCode, String responseBody, int weight, Long userId, boolean active) {
        MockResponse response = new MockResponse();
        response.setMockApi(mockApi);
        response.setStatusCode(statusCode);
        response.setContentType("application/json");
        response.setResponseBody(responseBody);
        response.setWeight(weight);
        response.setEnabled(true);
        response.setActive(active);
        response.setCreateTime(LocalDateTime.now());
        response.setUpdateTime(LocalDateTime.now());

        return mockResponseRepository.save(response);
    }

    /**
     * 预编译已存储的自定义响应处理器代码
     * <p>
     * 应用启动时，将数据库中存储的自定义响应处理器代码预编译到内存中，
     * 这样第一次请求时无需等待编译时间，提升响应速度。
     * </p>
     */
    @Operation(summary = "预编译自定义响应处理器")
    private void precompileCustomTransformers() {
        try {
            // 检查编译器是否可用
            if (!DynamicCompiler.isCompilerAvailable()) {
                log.warn("Java编译器不可用，跳过自定义响应处理器预编译");
                return;
            }

            // 查询所有包含自定义响应源码的接口
            List<MockApi> apisWithCustomSource = mockApiRepository.findAll().stream()
                    .filter(api -> api.getCustomResponseSource() != null
                            && !api.getCustomResponseSource().trim().isEmpty()
                            && api.getEnabled() != null
                            && api.getEnabled())
                    .toList();

            if (apisWithCustomSource.isEmpty()) {
                log.info("没有找到需要预编译的自定义响应处理器");
                return;
            }

            log.info("开始预编译 {} 个自定义响应处理器...", apisWithCustomSource.size());

            int successCount = 0;
            int failCount = 0;

            for (MockApi api : apisWithCustomSource) {
                try {
                    String source = api.getCustomResponseSource();
                    if (source != null && !source.trim().isEmpty()) {
                        // 编译并缓存
                        DynamicCompiler.compileAndInstantiate(api.getId(), source);
                        log.info("预编译成功: 接口={} (ID={})", api.getName(), api.getId());
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("预编译失败: 接口={} (ID={}), 错误: {}",
                            api.getName(), api.getId(), e.getMessage());
                    failCount++;
                }
            }

            log.info("自定义响应处理器预编译完成: 成功={}, 失败={}", successCount, failCount);

        } catch (Exception e) {
            log.error("预编译自定义响应处理器时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化系统配置默认值
     * <p>
     * 当配置表中不存在对应记录时，使用默认值写入数据库，
     * 确保 SystemConfigController 能返回完整的配置信息。
     * </p>
     */
    @Operation(summary = "初始化系统配置默认值")
    private void initSystemConfigDefaults() {
        try {
            initDefaultConfigIfAbsent("defaultResponseDelay", "0", "默认响应延迟（毫秒）");
            initDefaultConfigIfAbsent("maxResponseDelay", "5000", "最大响应延迟（毫秒）");
            initDefaultConfigIfAbsent("enableRequestLog", "true", "是否启用请求日志");
            initDefaultConfigIfAbsent("logRetentionDays", "30", "日志保留天数");
            initDefaultConfigIfAbsent("maxRequestBodySize", "10", "最大请求体大小（MB）");
            initDefaultConfigIfAbsent("axiosTimeout", "30000", "前端Axios请求超时时间（毫秒）");
            initDefaultConfigIfAbsent("customResponseCacheSeconds", "600", "自定义接口响应缓存时间（秒），0表示不缓存");

            // 注册配置默认值
            initDefaultConfigIfAbsent("enableRegistration", "false", "是否开启用户注册");
            initDefaultConfigIfAbsent("allowedEmailDomains", "", "允许注册的邮箱域名（逗号分隔）");
            initDefaultConfigIfAbsent("enableEmailVerification", "false", "是否开启邮箱验证");
            initDefaultConfigIfAbsent("siteBaseUrl", "", "系统访问地址（用于邮件模板中 {{siteUrl}} 占位符，如 https://example.com）");

            // 页脚默认配置
            initDefaultConfigIfAbsent("footerCopyright", "© 2026 carolcoral", "页脚版权信息");
            initDefaultConfigIfAbsent("footerFriendLinkUrl", "https://xindu.site", "友情链接URL");
            initDefaultConfigIfAbsent("footerFriendLinkTitle", "友情链接 - XINDU.SITE", "友情链接标题");
            initDefaultConfigIfAbsent("footerBlogUrl", "https://blog.xindu.site", "博客链接URL");
            initDefaultConfigIfAbsent("footerBlogTitle", "博客", "博客链接标题");
            initDefaultConfigIfAbsent("footerGithubUrl", "https://github.com/carolcoral", "GitHub链接URL");
            initDefaultConfigIfAbsent("footerGithubTitle", "GitHub", "GitHub链接标题");
            initDefaultConfigIfAbsent("footerEmailAddress", "lxw@cnkj.site", "邮箱地址");
            initDefaultConfigIfAbsent("footerEmailTitle", "发送邮件", "邮箱链接标题");

            log.info("系统配置默认值初始化完成");
        } catch (Exception e) {
            log.error("初始化系统配置默认值失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 如果配置不存在则使用默认值初始化
     */
    private void initDefaultConfigIfAbsent(String key, String defaultValue, String description) {
        try {
            if (systemConfigRepository.findByConfigKey(key).isEmpty()) {
                SystemConfig config = new SystemConfig();
                config.setConfigKey(key);
                config.setConfigValue(defaultValue);
                config.setDescription(description);
                config.setCreateTime(LocalDateTime.now());
                config.setUpdateTime(LocalDateTime.now());
                systemConfigRepository.save(config);
                log.debug("初始化系统配置默认值: {} = {}", key, defaultValue);
            }
        } catch (Exception e) {
            log.warn("初始化配置 {} 失败: {}", key, e.getMessage());
        }
    }

    /**
     * 系统默认模板名称常量
     */
    private static final String SYSTEM_TEMPLATE_NAME_DEFAULT = "【系统】标准响应包装器";
    private static final String SYSTEM_TEMPLATE_NAME_HTTPCLIENT = "【系统】HttpClient请求转发器";
    private static final String SYSTEM_TEMPLATE_NAME_DATA_MASKING = "【系统】数据脱敏处理器";
    private static final String SYSTEM_TEMPLATE_NAME_FIELD_TRANSFORM = "【系统】字段转换器";
    private static final String SYSTEM_TEMPLATE_NAME_CONDITIONAL = "【系统】条件响应处理器";
    private static final String SYSTEM_TEMPLATE_NAME_LOGGING = "【系统】日志记录器";

    /**
     * 初始化系统默认代码模板
     * <p>
     * 创建 6 个不可编辑/删除的系统默认模板（全局可用，不关联项目）：
     * 1. 标准响应包装器 - 将响应包装为统一格式
     * 2. HttpClient请求转发器 - 使用HttpClient转发请求到真实后端
     * 3. 数据脱敏处理器 - 对敏感字段进行脱敏
     * 4. 字段转换器 - 字段重命名/类型转换/值映射
     * 5. 条件响应处理器 - 根据请求参数返回不同响应
     * 6. 日志记录器 - 记录请求和响应信息
     * </p>
     */
    @Operation(summary = "初始化系统默认代码模板")
    private void initSystemCodeTemplates() {
        try {
            User admin = userRepository.findByUsername(adminUsername).orElse(null);
            Long adminUserId = admin != null ? admin.getId() : null;

            int createdCount = 0;
            int skippedCount = 0;

            // 检查并创建标准响应包装器模板（全局系统模板，project=null）
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_DEFAULT,
                    "【系统默认】标准响应包装器 - 将Mock响应包装为统一格式 {code, message, data, timestamp}",
                    getDefaultWrapperTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            // 检查并创建HttpClient请求转发器模板（全局系统模板，project=null）
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_HTTPCLIENT,
                    "【系统默认】HttpClient请求转发器 - 将请求转发到真实后端服务，适用于部分接口需要代理转发的场景",
                    getHttpClientForwarderTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            // 检查并创建数据脱敏处理器模板
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_DATA_MASKING,
                    "【系统默认】数据脱敏处理器 - 对响应中的手机号、邮箱、身份证等敏感字段进行脱敏处理",
                    getDataMaskingTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            // 检查并创建字段转换器模板
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_FIELD_TRANSFORM,
                    "【系统默认】字段转换器 - 对响应字段进行重命名、类型转换、值映射等操作",
                    getFieldTransformTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            // 检查并创建条件响应处理器模板
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_CONDITIONAL,
                    "【系统默认】条件响应处理器 - 根据请求参数返回不同的响应数据",
                    getConditionalResponseTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            // 检查并创建日志记录器模板
            if (createSystemTemplateIfAbsent(adminUserId,
                    SYSTEM_TEMPLATE_NAME_LOGGING,
                    "【系统默认】日志记录器 - 记录请求和响应的详细信息",
                    getLoggingTemplateCode())) {
                createdCount++;
            } else {
                skippedCount++;
            }

            log.info("系统默认代码模板初始化完成: 新建={}, 跳过(已存在)={}", createdCount, skippedCount);

        } catch (Exception e) {
            log.error("初始化系统默认代码模板失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 如果系统模板不存在则创建（全局模板，不关联项目）
     *
     * @param userId      创建用户ID
     * @param name        模板名称
     * @param description 模板描述
     * @param sourceCode  源代码
     * @return true 表示新建，false 表示已存在跳过
     */
    private boolean createSystemTemplateIfAbsent(Long userId,
                                                  String name, String description, String sourceCode) {
        // 检查是否已存在同名系统模板（全局）
        List<CustomCodeTemplate> systemTemplates = customCodeTemplateRepository.findByIsSystemTrue();
        Optional<CustomCodeTemplate> existing = systemTemplates.stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst();

        if (existing.isPresent()) {
            // 已存在则更新源码（确保模板始终是最新版本）
            CustomCodeTemplate template = existing.get();
            template.setSourceCode(sourceCode);
            template.setDescription(description);
            template.setUpdateTime(LocalDateTime.now());
            customCodeTemplateRepository.save(template);
            log.info("更新全局系统默认模板: name={}", name);
            return false;
        }

        CustomCodeTemplate template = new CustomCodeTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setSourceCode(sourceCode);
        template.setEnabled(true);
        template.setIsSystem(true);
        template.setProject(null);  // 系统模板不关联项目，全局可用
        template.setCreateUserId(userId);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());

        customCodeTemplateRepository.save(template);
        log.info("创建全局系统默认模板: name={}", name);
        return true;
    }

    /**
     * 获取默认标准响应包装器模板代码
     */
    private String getDefaultWrapperTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import com.alibaba.fastjson.JSON;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】标准响应包装器\n" +
                " * 将Mock响应包装为统一的标准格式 {code, message, data, timestamp}\n" +
                " */\n" +
                "public class SystemDefaultTransformer implements CustomResponseTransformer {\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        Object body = mockResponse.getBody();\n" +
                "        Map<String, Object> result = new LinkedHashMap<>();\n" +
                "        result.put(\"code\", mockResponse.getStatusCode());\n" +
                "        result.put(\"message\", \"success\");\n" +
                "        result.put(\"data\", body);\n" +
                "        result.put(\"timestamp\", System.currentTimeMillis());\n" +
                "\n" +
                "        return MockResponseDTO.builder()\n" +
                "                .statusCode(mockResponse.getStatusCode())\n" +
                "                .headers(mockResponse.getHeaders())\n" +
                "                .body(result)\n" +
                "                .delay(mockResponse.getDelay())\n" +
                "                .build();\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"标准格式包装器 - 将响应包装为 {code, message, data, timestamp} 格式\";\n" +
                "    }\n" +
                "}";
    }

    /**
     * 获取HttpClient请求转发器模板代码
     * <p>
     * 使用场景：某些接口需要透传到真实后端，而不是返回Mock数据。
     * 在接口编辑中选择此模板后，请求将被转发到配置的目标URL。
     * </p>
     */
    private String getHttpClientForwarderTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import java.net.URI;\n" +
                "import java.net.http.HttpClient;\n" +
                "import java.net.http.HttpRequest;\n" +
                "import java.net.http.HttpResponse;\n" +
                "import java.time.Duration;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】HttpClient请求转发器\n" +
                " * <p>\n" +
                " * 将当前请求转发到真实后端服务，获取真实响应并返回。\n" +
                " * 适用于部分接口需要走真实服务、部分接口需要Mock的场景。\n" +
                " * </p>\n" +
                " *\n" +
                " * <h3>配置说明</h3>\n" +
                " * <ul>\n" +
                " *   <li>TARGET_BASE_URL: 目标后端服务的根URL，根据实际环境修改</li>\n" +
                " *   <li>REQUEST_TIMEOUT_SECONDS: 请求超时时间（秒）</li>\n" +
                " *   <li>FORWARD_HEADERS: 需要从原始请求中透传的请求头列表</li>\n" +
                " * </ul>\n" +
                " *\n" +
                " * <h3>使用示例</h3>\n" +
                " * <ol>\n" +
                " *   <li>修改 TARGET_BASE_URL 为你的真实后端地址</li>\n" +
                " *   <li>根据需要调整透传的请求头</li>\n" +
                " *   <li>在接口编辑中选择此模板，保存后该接口将转发到真实后端</li>\n" +
                " * </ol>\n" +
                " */\n" +
                "public class HttpClientForwarder implements CustomResponseTransformer {\n" +
                "\n" +
                "    /** 目标后端服务根URL - 请根据实际环境修改 */\n" +
                "    private static final String TARGET_BASE_URL = \"http://localhost:8080\";\n" +
                "\n" +
                "    /** 请求超时时间（秒） */\n" +
                "    private static final int REQUEST_TIMEOUT_SECONDS = 30;\n" +
                "\n" +
                "    /** 需要从原始请求中透传到目标服务的请求头 */\n" +
                "    private static final Set<String> FORWARD_HEADERS = new HashSet<>(Arrays.asList(\n" +
                "        \"Content-Type\", \"Accept\", \"Authorization\", \"X-Request-Id\", \"X-Trace-Id\"\n" +
                "    ));\n" +
                "\n" +
                "    private final HttpClient httpClient = HttpClient.newBuilder()\n" +
                "            .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))\n" +
                "            .followRedirects(HttpClient.Redirect.NORMAL)\n" +
                "            .build();\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        try {\n" +
                "            // 构建目标URL：基础URL + 请求路径 + 查询参数\n" +
                "            StringBuilder urlBuilder = new StringBuilder(TARGET_BASE_URL);\n" +
                "            urlBuilder.append(mockRequest.getPath());\n" +
                "\n" +
                "            // 追加查询参数\n" +
                "            if (mockRequest.getParams() != null && !mockRequest.getParams().isEmpty()) {\n" +
                "                boolean first = true;\n" +
                "                for (Map.Entry<String, Object> entry : mockRequest.getParams().entrySet()) {\n" +
                "                    urlBuilder.append(first ? \"?\" : \"&\");\n" +
                "                    urlBuilder.append(entry.getKey()).append(\"=\").append(entry.getValue());\n" +
                "                    first = false;\n" +
                "                }\n" +
                "            }\n" +
                "\n" +
                "            URI targetUri = URI.create(urlBuilder.toString());\n" +
                "            String method = mockRequest.getMethod() != null ? mockRequest.getMethod().toUpperCase() : \"GET\";\n" +
                "\n" +
                "            // 构建转发请求\n" +
                "            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n" +
                "                    .uri(targetUri)\n" +
                "                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));\n" +
                "\n" +
                "            // 透传请求头\n" +
                "            if (mockRequest.getHeaders() != null) {\n" +
                "                for (Map.Entry<String, String> entry : mockRequest.getHeaders().entrySet()) {\n" +
                "                    String headerName = entry.getKey();\n" +
                "                    // 跳过HTTP/2受限的请求头\n" +
                "                    if (headerName == null || \"Host\".equalsIgnoreCase(headerName)\n" +
                "                            || \"Connection\".equalsIgnoreCase(headerName)) {\n" +
                "                        continue;\n" +
                "                    }\n" +
                "                    // 只透传白名单中的请求头，或所有请求头（如需要可调整）\n" +
                "                    if (FORWARD_HEADERS.contains(headerName)) {\n" +
                "                        requestBuilder.header(headerName, entry.getValue());\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "\n" +
                "            // 设置请求体（POST/PUT/PATCH等方法）\n" +
                "            if (mockRequest.getBody() != null\n" +
                "                    && (\"POST\".equals(method) || \"PUT\".equals(method) || \"PATCH\".equals(method))) {\n" +
                "                String bodyStr = mockRequest.getBody() instanceof String\n" +
                "                        ? (String) mockRequest.getBody()\n" +
                "                        : mockRequest.getBody().toString();\n" +
                "                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(bodyStr));\n" +
                "            } else {\n" +
                "                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());\n" +
                "            }\n" +
                "\n" +
                "            // 发送请求\n" +
                "            HttpRequest httpRequest = requestBuilder.build();\n" +
                "            HttpResponse<String> httpResponse = httpClient.send(httpRequest,\n" +
                "                    HttpResponse.BodyHandlers.ofString());\n" +
                "\n" +
                "            // 解析响应头\n" +
                "            Map<String, String> responseHeaders = new LinkedHashMap<>();\n" +
                "            httpResponse.headers().map().forEach((key, values) -> {\n" +
                "                if (values != null && !values.isEmpty()) {\n" +
                "                    responseHeaders.put(key, String.join(\", \", values));\n" +
                "                }\n" +
                "            });\n" +
                "\n" +
                "            // 解析响应体（尝试JSON，失败则保留原始字符串）\n" +
                "            Object responseBody;\n" +
                "            try {\n" +
                "                responseBody = com.alibaba.fastjson.JSON.parse(httpResponse.body());\n" +
                "            } catch (Exception e) {\n" +
                "                responseBody = httpResponse.body();\n" +
                "            }\n" +
                "\n" +
                "            // 构建MockResponseDTO返回\n" +
                "            return MockResponseDTO.builder()\n" +
                "                    .statusCode(httpResponse.statusCode())\n" +
                "                    .headers(responseHeaders)\n" +
                "                    .body(responseBody)\n" +
                "                    .delay(mockResponse.getDelay())\n" +
                "                    .build();\n" +
                "\n" +
                "        } catch (Exception e) {\n" +
                "            // 转发失败时返回错误信息\n" +
                "            Map<String, Object> errorResult = new LinkedHashMap<>();\n" +
                "            errorResult.put(\"code\", 502);\n" +
                "            errorResult.put(\"message\", \"请求转发失败: \" + e.getMessage());\n" +
                "            errorResult.put(\"targetUrl\", TARGET_BASE_URL + mockRequest.getPath());\n" +
                "            errorResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "\n" +
                "            return MockResponseDTO.builder()\n" +
                "                    .statusCode(502)\n" +
                "                    .headers(mockResponse.getHeaders())\n" +
                "                    .body(errorResult)\n" +
                "                    .delay(mockResponse.getDelay())\n" +
                "                    .build();\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"HttpClient请求转发器 - 将请求转发到真实后端服务并返回响应\";\n" +
                "    }\n" +
                "}";
    }

    /**
     * 获取数据脱敏处理器模板代码
     * <p>
     * 使用场景：对响应中的敏感信息（手机号、邮箱、身份证号、银行卡号等）进行脱敏处理。
     * </p>
     */
    private String getDataMaskingTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import com.alibaba.fastjson.JSON;\n" +
                "import com.alibaba.fastjson.JSONObject;\n" +
                "import com.alibaba.fastjson.JSONArray;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】数据脱敏处理器\n" +
                " * <p>\n" +
                " * 对响应中的敏感字段进行脱敏处理，保护用户隐私。\n" +
                " * 支持脱敏的字段：手机号、邮箱、身份证号、银行卡号、姓名等。\n" +
                " * </p>\n" +
                " *\n" +
                " * <h3>脱敏规则</h3>\n" +
                " * <ul>\n" +
                " *   <li>手机号：保留前3位和后4位，中间4位用****替代（138****1234）</li>\n" +
                " *   <li>邮箱：保留首字符和@后内容，中间用***替代（u***@example.com）</li>\n" +
                " *   <li>身份证号：保留前3位和后4位，中间用****替代</li>\n" +
                " *   <li>银行卡号：保留后4位，其余用****替代</li>\n" +
                " *   <li>姓名：保留姓，名用*替代</li>\n" +
                " * </ul>\n" +
                " */\n" +
                "public class DataMaskingTransformer implements CustomResponseTransformer {\n" +
                "\n" +
                "    /** 需要脱敏的字段名集合（不区分大小写） */\n" +
                "    private static final Set<String> PHONE_FIELDS = new HashSet<>(Arrays.asList(\n" +
                "        \"phone\", \"mobile\", \"tel\", \"telephone\", \"phoneNumber\", \"contactPhone\"\n" +
                "    ));\n" +
                "\n" +
                "    private static final Set<String> EMAIL_FIELDS = new HashSet<>(Arrays.asList(\n" +
                "        \"email\", \"mail\", \"eMail\", \"emailAddress\", \"contactEmail\"\n" +
                "    ));\n" +
                "\n" +
                "    private static final Set<String> ID_CARD_FIELDS = new HashSet<>(Arrays.asList(\n" +
                "        \"idCard\", \"idNumber\", \"identityCard\", \"idNo\", \"cardNo\"\n" +
                "    ));\n" +
                "\n" +
                "    private static final Set<String> BANK_CARD_FIELDS = new HashSet<>(Arrays.asList(\n" +
                "        \"bankCard\", \"bankAccount\", \"cardNumber\", \"bankCardNo\"\n" +
                "    ));\n" +
                "\n" +
                "    private static final Set<String> NAME_FIELDS = new HashSet<>(Arrays.asList(\n" +
                "        \"name\", \"realName\", \"userName\", \"fullName\", \"contactName\"\n" +
                "    ));\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        Object body = mockResponse.getBody();\n" +
                "        Object maskedBody = maskSensitiveData(body);\n" +
                "\n" +
                "        return MockResponseDTO.builder()\n" +
                "                .statusCode(mockResponse.getStatusCode())\n" +
                "                .headers(mockResponse.getHeaders())\n" +
                "                .body(maskedBody)\n" +
                "                .delay(mockResponse.getDelay())\n" +
                "                .build();\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     * 递归脱敏处理\n" +
                "     */\n" +
                "    @SuppressWarnings(\"unchecked\")\n" +
                "    private Object maskSensitiveData(Object data) {\n" +
                "        if (data == null) return null;\n" +
                "\n" +
                "        if (data instanceof JSONObject) {\n" +
                "            JSONObject obj = (JSONObject) data;\n" +
                "            JSONObject result = new JSONObject();\n" +
                "            for (String key : obj.keySet()) {\n" +
                "                Object value = obj.get(key);\n" +
                "                String lowerKey = key.toLowerCase();\n" +
                "\n" +
                "                if (PHONE_FIELDS.contains(lowerKey) && value instanceof String) {\n" +
                "                    result.put(key, maskPhone((String) value));\n" +
                "                } else if (EMAIL_FIELDS.contains(lowerKey) && value instanceof String) {\n" +
                "                    result.put(key, maskEmail((String) value));\n" +
                "                } else if (ID_CARD_FIELDS.contains(lowerKey) && value instanceof String) {\n" +
                "                    result.put(key, maskIdCard((String) value));\n" +
                "                } else if (BANK_CARD_FIELDS.contains(lowerKey) && value instanceof String) {\n" +
                "                    result.put(key, maskBankCard((String) value));\n" +
                "                } else if (NAME_FIELDS.contains(lowerKey) && value instanceof String) {\n" +
                "                    result.put(key, maskName((String) value));\n" +
                "                } else if (value instanceof JSONObject || value instanceof JSONArray) {\n" +
                "                    result.put(key, maskSensitiveData(value));\n" +
                "                } else {\n" +
                "                    result.put(key, value);\n" +
                "                }\n" +
                "            }\n" +
                "            return result;\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof JSONArray) {\n" +
                "            JSONArray arr = (JSONArray) data;\n" +
                "            JSONArray result = new JSONArray();\n" +
                "            for (int i = 0; i < arr.size(); i++) {\n" +
                "                result.add(maskSensitiveData(arr.get(i)));\n" +
                "            }\n" +
                "            return result;\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof Map) {\n" +
                "            return maskSensitiveData(new JSONObject((Map<String, Object>) data));\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof List) {\n" +
                "            return maskSensitiveData(new JSONArray((List<Object>) data));\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof String) {\n" +
                "            try {\n" +
                "                Object parsed = JSON.parse((String) data);\n" +
                "                if (parsed instanceof JSONObject || parsed instanceof JSONArray) {\n" +
                "                    return JSON.toJSONString(maskSensitiveData(parsed));\n" +
                "                }\n" +
                "            } catch (Exception ignored) {}\n" +
                "        }\n" +
                "\n" +
                "        return data;\n" +
                "    }\n" +
                "\n" +
                "    private String maskPhone(String phone) {\n" +
                "        if (phone == null || phone.length() < 7) return phone;\n" +
                "        return phone.replaceAll(\"(\\\\d{3})\\\\d{4}(\\\\d{4})\", \"$1****$2\");\n" +
                "    }\n" +
                "\n" +
                "    private String maskEmail(String email) {\n" +
                "        if (email == null || !email.contains(\"@\")) return email;\n" +
                "        int atIndex = email.indexOf('@');\n" +
                "        String prefix = email.substring(0, atIndex);\n" +
                "        String suffix = email.substring(atIndex);\n" +
                "        if (prefix.length() <= 2) {\n" +
                "            return prefix.charAt(0) + \"***\" + suffix;\n" +
                "        }\n" +
                "        return prefix.charAt(0) + \"***\" + prefix.charAt(prefix.length() - 1) + suffix;\n" +
                "    }\n" +
                "\n" +
                "    private String maskIdCard(String idCard) {\n" +
                "        if (idCard == null || idCard.length() < 8) return idCard;\n" +
                "        return idCard.replaceAll(\"(\\\\d{3})\\\\d+(\\\\d{4})\", \"$1****$2\");\n" +
                "    }\n" +
                "\n" +
                "    private String maskBankCard(String bankCard) {\n" +
                "        if (bankCard == null || bankCard.length() < 4) return bankCard;\n" +
                "        return \"****\" + bankCard.substring(bankCard.length() - 4);\n" +
                "    }\n" +
                "\n" +
                "    private String maskName(String name) {\n" +
                "        if (name == null || name.isEmpty()) return name;\n" +
                "        if (name.length() == 1) return \"*\";\n" +
                "        StringBuilder sb = new StringBuilder();\n" +
                "        sb.append(name.charAt(0));\n" +
                "        for (int i = 1; i < name.length(); i++) {\n" +
                "            sb.append('*');\n" +
                "        }\n" +
                "        return sb.toString();\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"数据脱敏处理器 - 对响应中的手机号、邮箱、身份证等敏感字段进行脱敏\";\n" +
                "    }\n" +
                "}";
    }

    /**
     * 获取字段转换器模板代码
     * <p>
     * 使用场景：对响应字段进行重命名、类型转换、值映射、字段筛选等操作。
     * </p>
     */
    private String getFieldTransformTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import com.alibaba.fastjson.JSON;\n" +
                "import com.alibaba.fastjson.JSONObject;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】字段转换器\n" +
                " * <p>\n" +
                " * 对响应中的字段进行重命名、类型转换、值映射、字段筛选等操作。\n" +
                " * 适用于前后端字段名不一致、需要统一字段格式等场景。\n" +
                " * </p>\n" +
                " *\n" +
                " * <h3>支持的转换</h3>\n" +
                " * <ul>\n" +
                " *   <li>字段重命名：下划线转驼峰、驼峰转下划线</li>\n" +
                " *   <li>值映射：将状态码映射为可读文本</li>\n" +
                " *   <li>字段筛选：只保留需要的字段</li>\n" +
                " *   <li>添加额外字段：如时间戳、接口版本等</li>\n" +
                " * </ul>\n" +
                " */\n" +
                "public class FieldTransformTransformer implements CustomResponseTransformer {\n" +
                "\n" +
                "    /** 状态码到可读文本的映射 */\n" +
                "    private static final Map<String, String> STATUS_MAP = new LinkedHashMap<>();\n" +
                "    static {\n" +
                "        STATUS_MAP.put(\"0\", \"待处理\");\n" +
                "        STATUS_MAP.put(\"1\", \"处理中\");\n" +
                "        STATUS_MAP.put(\"2\", \"已完成\");\n" +
                "        STATUS_MAP.put(\"3\", \"已取消\");\n" +
                "        STATUS_MAP.put(\"4\", \"已关闭\");\n" +
                "    }\n" +
                "\n" +
                "    /** 需要保留的字段（为空则保留所有字段） */\n" +
                "    private static final Set<String> KEEP_FIELDS = new HashSet<>();\n" +
                "    static {\n" +
                "        // 如需字段筛选，取消注释并添加需要的字段名（驼峰格式）\n" +
                "        // KEEP_FIELDS.add(\"id\");\n" +
                "        // KEEP_FIELDS.add(\"name\");\n" +
                "        // KEEP_FIELDS.add(\"status\");\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        Object body = mockResponse.getBody();\n" +
                "        Object transformed = transformFields(body);\n" +
                "\n" +
                "        return MockResponseDTO.builder()\n" +
                "                .statusCode(mockResponse.getStatusCode())\n" +
                "                .headers(mockResponse.getHeaders())\n" +
                "                .body(transformed)\n" +
                "                .delay(mockResponse.getDelay())\n" +
                "                .build();\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     * 递归转换字段\n" +
                "     */\n" +
                "    @SuppressWarnings(\"unchecked\")\n" +
                "    private Object transformFields(Object data) {\n" +
                "        if (data == null) return null;\n" +
                "\n" +
                "        if (data instanceof Map) {\n" +
                "            Map<String, Object> source = (Map<String, Object>) data;\n" +
                "            Map<String, Object> result = new LinkedHashMap<>();\n" +
                "\n" +
                "            for (Map.Entry<String, Object> entry : source.entrySet()) {\n" +
                "                String key = entry.getKey();\n" +
                "                Object value = entry.getValue();\n" +
                "\n" +
                "                // 下划线转驼峰\n" +
                "                String camelKey = toCamelCase(key);\n" +
                "\n" +
                "                // 字段筛选：如果配置了保留字段且当前字段不在列表中则跳过\n" +
                "                if (!KEEP_FIELDS.isEmpty() && !KEEP_FIELDS.contains(camelKey)) {\n" +
                "                    continue;\n" +
                "                }\n" +
                "\n" +
                "                // 值映射：如果是状态字段，尝试映射为可读文本\n" +
                "                if (\"status\".equals(camelKey) && value instanceof String) {\n" +
                "                    String statusText = STATUS_MAP.get(value);\n" +
                "                    if (statusText != null) {\n" +
                "                        result.put(camelKey + \"Text\", statusText);\n" +
                "                    }\n" +
                "                }\n" +
                "\n" +
                "                // 递归处理嵌套对象和数组\n" +
                "                if (value instanceof Map || value instanceof List) {\n" +
                "                    result.put(camelKey, transformFields(value));\n" +
                "                } else {\n" +
                "                    result.put(camelKey, value);\n" +
                "                }\n" +
                "            }\n" +
                "\n" +
                "            // 添加额外字段\n" +
                "            result.put(\"timestamp\", System.currentTimeMillis());\n" +
                "\n" +
                "            return result;\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof List) {\n" +
                "            List<Object> source = (List<Object>) data;\n" +
                "            List<Object> result = new ArrayList<>();\n" +
                "            for (Object item : source) {\n" +
                "                result.add(transformFields(item));\n" +
                "            }\n" +
                "            return result;\n" +
                "        }\n" +
                "\n" +
                "        if (data instanceof String) {\n" +
                "            try {\n" +
                "                Object parsed = JSON.parse((String) data);\n" +
                "                if (parsed instanceof Map || parsed instanceof List) {\n" +
                "                    return JSON.toJSONString(transformFields(parsed));\n" +
                "                }\n" +
                "            } catch (Exception ignored) {}\n" +
                "        }\n" +
                "\n" +
                "        return data;\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     * 下划线命名转驼峰命名\n" +
                "     */\n" +
                "    private String toCamelCase(String input) {\n" +
                "        if (input == null || input.isEmpty()) return input;\n" +
                "        StringBuilder sb = new StringBuilder();\n" +
                "        boolean nextUpper = false;\n" +
                "        for (int i = 0; i < input.length(); i++) {\n" +
                "            char c = input.charAt(i);\n" +
                "            if (c == '_') {\n" +
                "                nextUpper = true;\n" +
                "            } else {\n" +
                "                sb.append(nextUpper ? Character.toUpperCase(c) : c);\n" +
                "                nextUpper = false;\n" +
                "            }\n" +
                "        }\n" +
                "        return sb.toString();\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"字段转换器 - 字段重命名、类型转换、值映射、字段筛选\";\n" +
                "    }\n" +
                "}";
    }

    /**
     * 获取条件响应处理器模板代码
     * <p>
     * 使用场景：根据请求参数、请求头等条件返回不同的响应数据。
     * </p>
     */
    private String getConditionalResponseTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import com.alibaba.fastjson.JSON;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】条件响应处理器\n" +
                " * <p>\n" +
                " * 根据请求参数、请求头、路径参数等条件动态返回不同的响应数据。\n" +
                " * 适用于需要根据业务规则返回不同Mock数据的场景。\n" +
                " * </p>\n" +
                " *\n" +
                " * <h3>支持的条件类型</h3>\n" +
                " * <ul>\n" +
                " *   <li>查询参数条件：根据URL参数返回不同数据</li>\n" +
                " *   <li>请求头条件：根据请求头值（如X-Mock-Scenario）控制响应</li>\n" +
                " *   <li>路径参数条件：根据RESTful路径参数区分响应</li>\n" +
                " *   <li>请求体条件：根据POST请求体中的字段决定响应</li>\n" +
                " * </ul>\n" +
                " */\n" +
                "public class ConditionalResponseTransformer implements CustomResponseTransformer {\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        Object body = mockResponse.getBody();\n" +
                "\n" +
                "        // 1. 根据请求头 X-Mock-Scenario 控制响应场景\n" +
                "        String scenario = getHeader(mockRequest, \"X-Mock-Scenario\");\n" +
                "\n" +
                "        // 2. 根据查询参数决定响应\n" +
                "        String type = getParam(mockRequest, \"type\");\n" +
                "\n" +
                "        // 3. 根据条件构建不同的响应\n" +
                "        Object resultBody;\n" +
                "        int statusCode = mockResponse.getStatusCode();\n" +
                "\n" +
                "        if (\"error\".equalsIgnoreCase(scenario)) {\n" +
                "            // 场景1：模拟错误响应\n" +
                "            Map<String, Object> errorResult = new LinkedHashMap<>();\n" +
                "            errorResult.put(\"code\", 500);\n" +
                "            errorResult.put(\"message\", \"服务器内部错误\");\n" +
                "            errorResult.put(\"data\", null);\n" +
                "            errorResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "            resultBody = errorResult;\n" +
                "            statusCode = 500;\n" +
                "\n" +
                "        } else if (\"empty\".equalsIgnoreCase(scenario)) {\n" +
                "            // 场景2：模拟空数据响应\n" +
                "            Map<String, Object> emptyResult = new LinkedHashMap<>();\n" +
                "            emptyResult.put(\"code\", 200);\n" +
                "            emptyResult.put(\"message\", \"success\");\n" +
                "            emptyResult.put(\"data\", new ArrayList<>());\n" +
                "            emptyResult.put(\"total\", 0);\n" +
                "            emptyResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "            resultBody = emptyResult;\n" +
                "\n" +
                "        } else if (\"timeout\".equalsIgnoreCase(scenario)) {\n" +
                "            // 场景3：模拟超时响应（通过设置较长延迟）\n" +
                "            Map<String, Object> timeoutResult = new LinkedHashMap<>();\n" +
                "            timeoutResult.put(\"code\", 504);\n" +
                "            timeoutResult.put(\"message\", \"网关超时\");\n" +
                "            timeoutResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "            resultBody = timeoutResult;\n" +
                "            statusCode = 504;\n" +
                "\n" +
                "        } else if (\"list\".equals(type)) {\n" +
                "            // 场景4：根据type参数返回列表格式\n" +
                "            Map<String, Object> listResult = new LinkedHashMap<>();\n" +
                "            listResult.put(\"code\", 200);\n" +
                "            listResult.put(\"message\", \"success\");\n" +
                "            listResult.put(\"data\", body);\n" +
                "            listResult.put(\"total\", 1);\n" +
                "            listResult.put(\"page\", 1);\n" +
                "            listResult.put(\"pageSize\", 20);\n" +
                "            listResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "            resultBody = listResult;\n" +
                "\n" +
                "        } else {\n" +
                "            // 默认场景：正常包装响应\n" +
                "            Map<String, Object> defaultResult = new LinkedHashMap<>();\n" +
                "            defaultResult.put(\"code\", statusCode);\n" +
                "            defaultResult.put(\"message\", \"success\");\n" +
                "            defaultResult.put(\"data\", body);\n" +
                "            defaultResult.put(\"timestamp\", System.currentTimeMillis());\n" +
                "            resultBody = defaultResult;\n" +
                "        }\n" +
                "\n" +
                "        return MockResponseDTO.builder()\n" +
                "                .statusCode(statusCode)\n" +
                "                .headers(mockResponse.getHeaders())\n" +
                "                .body(resultBody)\n" +
                "                .delay(mockResponse.getDelay())\n" +
                "                .build();\n" +
                "    }\n" +
                "\n" +
                "    private String getHeader(MockRequest request, String name) {\n" +
                "        if (request.getHeaders() == null) return null;\n" +
                "        return request.getHeaders().get(name);\n" +
                "    }\n" +
                "\n" +
                "    private String getParam(MockRequest request, String name) {\n" +
                "        if (request.getParams() == null) return null;\n" +
                "        Object value = request.getParams().get(name);\n" +
                "        return value != null ? value.toString() : null;\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"条件响应处理器 - 根据请求参数/请求头返回不同的响应数据\";\n" +
                "    }\n" +
                "}";
    }

    /**
     * 获取日志记录器模板代码
     * <p>
     * 使用场景：记录请求和响应的详细信息，便于调试和监控。
     * </p>
     */
    private String getLoggingTemplateCode() {
        return "import com.carolcoral.apiserver.dto.MockRequest;\n" +
                "import com.carolcoral.apiserver.dto.MockResponseDTO;\n" +
                "import com.carolcoral.apiserver.plugin.CustomResponseTransformer;\n" +
                "import java.util.*;\n" +
                "import com.alibaba.fastjson.JSON;\n" +
                "\n" +
                "/**\n" +
                " * 【系统默认模板】日志记录器\n" +
                " * <p>\n" +
                " * 在请求处理过程中记录请求和响应的详细信息，便于调试、审计和监控。\n" +
                " * 不会修改响应内容，只做日志记录。\n" +
                " * </p>\n" +
                " *\n" +
                " * <h3>记录内容</h3>\n" +
                " * <ul>\n" +
                " *   <li>请求信息：方法、路径、请求头、参数、请求体</li>\n" +
                " *   <li>响应信息：状态码、响应体长度、处理耗时</li>\n" +
                " *   <li>接口元信息：接口名称、接口路径</li>\n" +
                " * </ul>\n" +
                " */\n" +
                "public class LoggingTransformer implements CustomResponseTransformer {\n" +
                "\n" +
                "    /** 是否记录请求体（可能包含敏感信息，生产环境建议关闭） */\n" +
                "    private static final boolean LOG_REQUEST_BODY = true;\n" +
                "\n" +
                "    /** 是否记录响应体 */\n" +
                "    private static final boolean LOG_RESPONSE_BODY = false;\n" +
                "\n" +
                "    /** 请求体最大记录长度 */\n" +
                "    private static final int MAX_BODY_LOG_LENGTH = 2000;\n" +
                "\n" +
                "    @Override\n" +
                "    public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,\n" +
                "                                      String apiName, String apiPath) {\n" +
                "        long startTime = System.currentTimeMillis();\n" +
                "\n" +
                "        // 构建请求日志\n" +
                "        StringBuilder logBuilder = new StringBuilder();\n" +
                "        logBuilder.append(\"\\n========== Mock请求日志 ==========\\n\");\n" +
                "        logBuilder.append(\"接口名称: \").append(apiName != null ? apiName : \"未命名\").append(\"\\n\");\n" +
                "        logBuilder.append(\"接口路径: \").append(apiPath).append(\"\\n\");\n" +
                "        logBuilder.append(\"请求方法: \").append(mockRequest.getMethod()).append(\"\\n\");\n" +
                "        logBuilder.append(\"请求路径: \").append(mockRequest.getPath()).append(\"\\n\");\n" +
                "        logBuilder.append(\"项目编码: \").append(mockRequest.getProjectCode()).append(\"\\n\");\n" +
                "\n" +
                "        // 记录请求头\n" +
                "        if (mockRequest.getHeaders() != null && !mockRequest.getHeaders().isEmpty()) {\n" +
                "            logBuilder.append(\"请求头: \");\n" +
                "            for (Map.Entry<String, String> entry : mockRequest.getHeaders().entrySet()) {\n" +
                "                // 跳过敏感请求头\n" +
                "                if (\"Authorization\".equalsIgnoreCase(entry.getKey())\n" +
                "                        || \"Cookie\".equalsIgnoreCase(entry.getKey())) {\n" +
                "                    logBuilder.append(entry.getKey()).append(\"=***, \");\n" +
                "                } else {\n" +
                "                    logBuilder.append(entry.getKey()).append(\"=\").append(entry.getValue()).append(\", \");\n" +
                "                }\n" +
                "            }\n" +
                "            logBuilder.append(\"\\n\");\n" +
                "        }\n" +
                "\n" +
                "        // 记录查询参数\n" +
                "        if (mockRequest.getParams() != null && !mockRequest.getParams().isEmpty()) {\n" +
                "            logBuilder.append(\"查询参数: \").append(JSON.toJSONString(mockRequest.getParams())).append(\"\\n\");\n" +
                "        }\n" +
                "\n" +
                "        // 记录路径参数\n" +
                "        if (mockRequest.getPathParams() != null && !mockRequest.getPathParams().isEmpty()) {\n" +
                "            logBuilder.append(\"路径参数: \").append(JSON.toJSONString(mockRequest.getPathParams())).append(\"\\n\");\n" +
                "        }\n" +
                "\n" +
                "        // 记录请求体\n" +
                "        if (LOG_REQUEST_BODY && mockRequest.getBody() != null) {\n" +
                "            String bodyStr = mockRequest.getBody().toString();\n" +
                "            if (bodyStr.length() > MAX_BODY_LOG_LENGTH) {\n" +
                "                bodyStr = bodyStr.substring(0, MAX_BODY_LOG_LENGTH) + \"... (已截断)\";\n" +
                "            }\n" +
                "            logBuilder.append(\"请求体: \").append(bodyStr).append(\"\\n\");\n" +
                "        }\n" +
                "\n" +
                "        // 记录响应信息\n" +
                "        logBuilder.append(\"响应状态码: \").append(mockResponse.getStatusCode()).append(\"\\n\");\n" +
                "        if (mockResponse.getBody() != null) {\n" +
                "            String respStr = mockResponse.getBody().toString();\n" +
                "            logBuilder.append(\"响应体长度: \").append(respStr.length()).append(\" 字符\\n\");\n" +
                "            if (LOG_RESPONSE_BODY) {\n" +
                "                if (respStr.length() > MAX_BODY_LOG_LENGTH) {\n" +
                "                    respStr = respStr.substring(0, MAX_BODY_LOG_LENGTH) + \"... (已截断)\";\n" +
                "                }\n" +
                "                logBuilder.append(\"响应体: \").append(respStr).append(\"\\n\");\n" +
                "            }\n" +
                "        }\n" +
                "        if (mockResponse.getDelay() != null) {\n" +
                "            logBuilder.append(\"响应延迟: \").append(mockResponse.getDelay()).append(\"ms\\n\");\n" +
                "        }\n" +
                "\n" +
                "        long elapsed = System.currentTimeMillis() - startTime;\n" +
                "        logBuilder.append(\"处理耗时: \").append(elapsed).append(\"ms\\n\");\n" +
                "        logBuilder.append(\"====================================\\n\");\n" +
                "\n" +
                "        // 输出日志\n" +
                "        System.out.println(logBuilder.toString());\n" +
                "\n" +
                "        // 不修改响应内容，直接返回\n" +
                "        return mockResponse;\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String getDescription() {\n" +
                "        return \"日志记录器 - 记录请求和响应的详细信息\";\n" +
                "    }\n" +
                "}";
    }
}
