/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.PageResult;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.plugin.DynamicCompiler;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.util.CacheUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自定义接口服务类
 *
 * @author carolcoral
 */
@Tag(name = "接口服务", description = "自定义接口业务逻辑处理")
@Service
public class MockApiService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockApiService.class);

    /**
     * 构造器
     */
    public MockApiService(MockApiRepository mockApiRepository, MockResponseRepository mockResponseRepository, ProjectRepository projectRepository, CacheUtil cacheUtil, UserRepository userRepository, MockService mockService) {
        this.mockApiRepository = mockApiRepository;
        this.mockResponseRepository = mockResponseRepository;
        this.projectRepository = projectRepository;
        this.cacheUtil = cacheUtil;
        this.userRepository = userRepository;
        this.mockService = mockService;
    }

    private final MockApiRepository mockApiRepository;
    private final MockResponseRepository mockResponseRepository;
    private final ProjectRepository projectRepository;
    private final CacheUtil cacheUtil;
    private final UserRepository userRepository;
    private final MockService mockService;

    /**
     * 检查当前登录用户是否拥有指定权限（ADMIN 角色或指定 authority）
     */
    private boolean hasAuthorityOrAdmin(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authStr = ga.getAuthority();
            if ("ROLE_ADMIN".equals(authStr) || authority.equals(authStr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建接口
     *
     * @param mockApi 接口信息
     * @param userId  创建人ID
     * @return 创建的接口
     */
    @Operation(summary = "创建接口")
    @Transactional
    public ApiResponse<MockApi> createMockApi(@Parameter(description = "接口信息") MockApi mockApi, @Parameter(description = "创建人ID") Long userId) {
        try {
            // 检查项目是否存在
            Long projectId = mockApi.getProject() != null ? mockApi.getProject().getId() : null;
            if (projectId == null) {
                return ApiResponse.error("项目ID不能为空");
            }

            Optional<Project> projectOpt = projectRepository.findById(projectId);
            if (!projectOpt.isPresent()) {
                return ApiResponse.error("项目不存在");
            }

            mockApi.setProject(projectOpt.get());

            // 检查当前项目下接口路径和请求方法是否已存在
            if (mockApiRepository.existsByProjectIdAndPathAndMethod(projectId, mockApi.getPath(), mockApi.getMethod())) {
                return ApiResponse.error("当前项目下接口路径和请求方法已存在");
            }

            // 设置创建人
            mockApi.setCreateUserId(userId);

            // 设置默认值
            if (mockApi.getEnabled() == null) {
                mockApi.setEnabled(true);
            }
            if (mockApi.getEnableRandom() == null) {
                mockApi.setEnableRandom(false);
            }

            MockApi savedApi = mockApiRepository.save(mockApi);

            // 创建默认的 200 响应
            MockResponse defaultResponse = new MockResponse();
            defaultResponse.setMockApi(savedApi);
            defaultResponse.setStatusCode(200);
            defaultResponse.setContentType("application/json");
            defaultResponse.setHeaders("");
            defaultResponse.setResponseBody("{\"code\": 200, \"message\": \"success\", \"data\": {}}");
            defaultResponse.setWeight(100);
            defaultResponse.setEnabled(true);
            defaultResponse.setActive(true);
            mockResponseRepository.save(defaultResponse);

            // 缓存接口
            cacheUtil.cacheApi(savedApi);

            // 缓存响应
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(savedApi.getId());
            cacheUtil.cacheApiResponses(savedApi.getId(), responses);

            log.info("创建接口成功: {}/{} {}", savedApi.getProject().getCode(), savedApi.getPath(), savedApi.getMethod());
            return ApiResponse.success(savedApi);

        } catch (Exception e) {
            log.error("创建接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建接口失败，请稍后重试");
        }
    }

    /**
     * 更新接口
     *
     * @param mockApi 接口信息
     * @return 更新的接口
     */
    @Operation(summary = "更新接口")
    @Transactional
    public ApiResponse<MockApi> updateMockApi(@Parameter(description = "接口信息") MockApi mockApi) {
        try {
            Optional<MockApi> existingApiOpt = mockApiRepository.findById(mockApi.getId());

            if (!existingApiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            MockApi existingApi = existingApiOpt.get();

            // 检查当前项目下接口路径和请求方法是否被其他接口使用
            Long projectId = existingApi.getProject() != null ? existingApi.getProject().getId() : null;
            if (!existingApi.getPath().equals(mockApi.getPath()) ||
                    !existingApi.getMethod().equals(mockApi.getMethod())) {
                if (projectId != null && mockApiRepository.existsByProjectIdAndPathAndMethod(
                        projectId, mockApi.getPath(), mockApi.getMethod())) {
                    return ApiResponse.error("当前项目下接口路径和请求方法已存在");
                }
            }

            // 更新接口信息
            if (mockApi.getName() != null) {
                existingApi.setName(mockApi.getName());
            }
            if (mockApi.getPath() != null) {
                existingApi.setPath(mockApi.getPath());
            }
            if (mockApi.getMethod() != null) {
                existingApi.setMethod(mockApi.getMethod());
            }
            if (mockApi.getRequestType() != null) {
                existingApi.setRequestType(mockApi.getRequestType());
            }
            if (mockApi.getDescription() != null) {
                existingApi.setDescription(mockApi.getDescription());
            }
            if (mockApi.getEnabled() != null) {
                existingApi.setEnabled(mockApi.getEnabled());
            }
            if (mockApi.getResponseDelay() != null) {
                existingApi.setResponseDelay(mockApi.getResponseDelay());
            }
            if (mockApi.getEnableRandom() != null) {
                existingApi.setEnableRandom(mockApi.getEnableRandom());
            }
            if (mockApi.getCustomResponseHandler() != null) {
                existingApi.setCustomResponseHandler(mockApi.getCustomResponseHandler());
            }
            if (mockApi.getCustomResponseSource() != null) {
                existingApi.setCustomResponseSource(mockApi.getCustomResponseSource());
                // 清除动态编译缓存，确保下次请求时重新编译新代码
                DynamicCompiler.evictCache(mockApi.getId());
                log.info("清除动态编译缓存: apiId={}", mockApi.getId());
            }
            // 无论是否修改了自定义响应处理器，都清除响应结果缓存
            // 确保更新接口后，下一次请求能使用最新配置生成响应
            mockService.evictCustomResponseCache(mockApi.getId());

            MockApi updatedApi = mockApiRepository.save(existingApi);

            // 重新从数据库加载完整对象（确保 project 等懒加载关联被正确加载），
            // 否则 cacheApi 中因 project 为 null 而不会更新带项目ID的缓存 key，
            // 导致请求处理时仍命中旧缓存（customResponseSource 为空），代码模板不生效。
            MockApi reloadedApi = mockApiRepository.findById(updatedApi.getId()).orElse(updatedApi);

            // 更新缓存
            cacheUtil.cacheApi(reloadedApi);

            log.info("更新接口成功: {}/{} {}", reloadedApi.getProject().getCode(), reloadedApi.getPath(), reloadedApi.getMethod());
            return ApiResponse.success(reloadedApi);

        } catch (Exception e) {
            log.error("更新接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新接口失败，请稍后重试");
        }
    }

    /**
     * 删除接口
     *
     * @param apiId 接口ID
     * @return 删除结果
     */
    @Operation(summary = "删除接口")
    @Transactional
    public ApiResponse<Void> deleteMockApi(@Parameter(description = "接口ID", example = "1") Long apiId) {
        try {
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);
            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            MockApi api = apiOpt.get();
            Long projectId = api.getProject() != null ? api.getProject().getId() : null;

            // 删除接口下的所有响应
            mockResponseRepository.deleteByMockApiId(apiId);

            // 删除接口
            mockApiRepository.deleteById(apiId);

            // 清除缓存
            cacheUtil.evictApiCache(apiId);

            // 清除动态编译缓存
            DynamicCompiler.evictCache(apiId);

            // 清除自定义响应结果缓存
            mockService.evictCustomResponseCache(apiId);

            // 清除项目接口缓存
            if (projectId != null) {
                cacheUtil.evictProjectCache(projectId);
            }

            log.info("删除接口成功: {}", apiId);
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("删除接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除接口失败，请稍后重试");
        }
    }

    /**
     * 根据ID查询接口
     *
     * @param apiId 接口ID
     * @return 接口信息
     */
    @Operation(summary = "根据ID查询接口")
    public ApiResponse<MockApi> getMockApiById(@Parameter(description = "接口ID", example = "1") Long apiId) {
        try {
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);

            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            return ApiResponse.success(apiOpt.get());

        } catch (Exception e) {
            log.error("查询接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口失败，请稍后重试");
        }
    }

    /**
     * 根据接口路径和请求方法查询接口
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 接口信息
     */
    @Operation(summary = "根据接口路径和请求方法查询接口")
    public ApiResponse<MockApi> getMockApiByPathAndMethod(@Parameter(description = "接口路径", example = "/api/user") String path,
                                                           @Parameter(description = "请求方法", example = "GET") MockApi.HttpMethod method) {
        try {
            Optional<MockApi> apiOpt = cacheUtil.getApiFromCache(path, method);

            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            return ApiResponse.success(apiOpt.get());

        } catch (Exception e) {
            log.error("查询接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口失败，请稍后重试");
        }
    }

    /**
     * 根据项目ID查询接口列表
     *
     * @param projectId 项目ID
     * @return 接口列表
     */
    @Operation(summary = "根据项目ID查询接口列表")
    public ApiResponse<List<MockApi>> getMockApisByProjectId(@Parameter(description = "项目ID", example = "1") Long projectId) {
        try {
            // 权限校验：检查当前用户是否有权限访问该项目
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ApiResponse.error("未登录，无法查看项目接口");
            }

            Optional<User> userOpt = userRepository.findByUsername(auth.getName());
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User user = userOpt.get();

            // 管理员或有 api:view_all 权限的用户可以查看所有项目的接口
            if (!hasAuthorityOrAdmin("api:view_all")) {
                // 检查用户是否有权限访问该项目（仅限成员项目）
                List<Long> accessibleProjectIds = projectRepository.findAccessibleProjectsByUserId(user.getId())
                        .stream()
                        .map(Project::getId)
                        .collect(Collectors.toList());

                if (!accessibleProjectIds.contains(projectId)) {
                    return ApiResponse.error("没有权限访问该项目的接口");
                }
            }

            List<MockApi> apis = cacheUtil.getProjectApisFromCache(projectId);

            if (apis == null) {
                apis = mockApiRepository.findByProjectId(projectId);
                cacheUtil.cacheProjectApis(projectId, apis);
            }

            return ApiResponse.success(apis);

        } catch (Exception e) {
            log.error("查询接口列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口列表失败，请稍后重试");
        }
    }

    /**
     * 查询所有接口
     *
     * @return 接口列表
     */
    @Operation(summary = "查询所有接口")
    public ApiResponse<List<MockApi>> getAllMockApis() {
        try {
            List<MockApi> allApis = mockApiRepository.findAll();

            // 获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                // 未登录，返回空列表
                return ApiResponse.success(List.of());
            }

            // 获取用户角色
            Optional<User> userOpt = userRepository.findByUsername(auth.getName());
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User user = userOpt.get();

            // 管理员或有 api:view_all 权限的用户可以查看所有接口
            if (hasAuthorityOrAdmin("api:view_all")) {
                return ApiResponse.success(allApis);
            }

            // 普通用户只能查看自己是成员的项目下的接口
            List<Long> accessibleProjectIds = projectRepository.findAccessibleProjectsByUserId(user.getId())
                    .stream()
                    .map(Project::getId)
                    .collect(Collectors.toList());

            List<MockApi> accessibleApis = allApis.stream()
                    .filter(api -> api.getProject() != null && accessibleProjectIds.contains(api.getProject().getId()))
                    .collect(Collectors.toList());

            return ApiResponse.success(accessibleApis);

        } catch (Exception e) {
            log.error("查询接口列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口列表失败，请稍后重试");
        }
    }

    /**
     * 分页搜索所有接口
     */
    @Operation(summary = "分页搜索接口")
    public ApiResponse<PageResult<MockApi>> searchMockApis(String name, String path, MockApi.HttpMethod method,
            Long projectId, Boolean enabled, int page, int size) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ApiResponse.success(new PageResult<>());
            }
            Optional<User> userOpt = userRepository.findByUsername(auth.getName());
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }
            User user = userOpt.get();

            List<Long> accessibleProjectIds = null;
            if (!hasAuthorityOrAdmin("api:view_all")) {
                accessibleProjectIds = projectRepository.findAccessibleProjectsByUserId(user.getId())
                        .stream().map(Project::getId).collect(Collectors.toList());
                // 用户没有任何可访问的项目，直接返回空结果
                if (accessibleProjectIds.isEmpty()) {
                    return ApiResponse.success(new PageResult<>());
                }
            }

            Specification<MockApi> spec = buildMockApiSpec(name, path, method, projectId, enabled, accessibleProjectIds);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<MockApi> result = mockApiRepository.findAll(spec, pageRequest);
            return ApiResponse.success(toPageResult(result));
        } catch (Exception e) {
            log.error("分页搜索接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口列表失败，请稍后重试");
        }
    }

    /**
     * 分页搜索指定项目下的接口（需校验用户是否有权限访问该项目）
     */
    @Operation(summary = "分页搜索项目接口")
    public ApiResponse<PageResult<MockApi>> searchMockApisByProject(Long projectId, String name, String path,
            MockApi.HttpMethod method, Boolean enabled, int page, int size) {
        try {
            // 权限校验：检查当前用户是否有权限访问该项目
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
                return ApiResponse.error("未登录，无法查看项目接口");
            }

            Optional<User> userOpt = userRepository.findByUsername(auth.getName());
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User user = userOpt.get();

            // 管理员或有 api:view_all 权限的用户可以查看所有项目的接口
            if (!hasAuthorityOrAdmin("api:view_all")) {
                List<Long> accessibleProjectIds = projectRepository.findAccessibleProjectsByUserId(user.getId())
                        .stream()
                        .map(Project::getId)
                        .collect(Collectors.toList());

                if (!accessibleProjectIds.contains(projectId)) {
                    return ApiResponse.error("没有权限访问该项目的接口");
                }
            }

            Specification<MockApi> spec = buildMockApiSpec(name, path, method, projectId, enabled, null);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<MockApi> result = mockApiRepository.findAll(spec, pageRequest);
            return ApiResponse.success(toPageResult(result));
        } catch (Exception e) {
            log.error("分页搜索项目接口失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口列表失败，请稍后重试");
        }
    }

    private Specification<MockApi> buildMockApiSpec(String name, String path, MockApi.HttpMethod method,
            Long projectId, Boolean enabled, List<Long> accessibleProjectIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (path != null && !path.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("path")), "%" + path.toLowerCase() + "%"));
            }
            if (method != null) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (accessibleProjectIds != null && !accessibleProjectIds.isEmpty()) {
                predicates.add(root.get("project").get("id").in(accessibleProjectIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private <T> PageResult<T> toPageResult(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setContent(page.getContent());
        result.setPage(page.getNumber());
        result.setSize(page.getSize());
        result.setTotalElements(page.getTotalElements());
        result.setTotalPages(page.getTotalPages());
        result.setFirst(page.isFirst());
        result.setLast(page.isLast());
        return result;
    }

    /**
     * 查询启用状态的接口
     *
     * @return 接口列表
     */
    @Operation(summary = "查询启用状态的接口")
    public ApiResponse<List<MockApi>> getEnabledMockApis() {
        try {
            List<MockApi> apis = mockApiRepository.findByEnabled(true);
            return ApiResponse.success(apis);

        } catch (Exception e) {
            log.error("查询接口列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询接口列表失败，请稍后重试");
        }
    }

    /**
     * 切换接口状态
     *
     * @param apiId 接口ID
     * @return 操作结果
     */
    @Operation(summary = "切换接口状态")
    @Transactional
    public ApiResponse<MockApi> toggleApiStatus(@Parameter(description = "接口ID", example = "1") Long apiId) {
        try {
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);

            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            MockApi api = apiOpt.get();
            api.setEnabled(!api.getEnabled());

            MockApi updatedApi = mockApiRepository.save(api);

            // 更新缓存
            cacheUtil.cacheApi(updatedApi);

            log.info("切换接口状态成功: {} -> {}", apiId, updatedApi.getEnabled());
            return ApiResponse.success(updatedApi);

        } catch (Exception e) {
            log.error("切换接口状态失败: {}", e.getMessage(), e);
            return ApiResponse.error("切换接口状态失败，请稍后重试");
        }
    }

    /**
     * 添加接口响应
     *
     * @param apiId         接口ID
     * @param mockResponse  响应信息
     * @return 添加的响应
     */
    @Operation(summary = "添加接口响应")
    @Transactional
    public ApiResponse<MockResponse> addApiResponse(@Parameter(description = "接口ID", example = "1") Long apiId,
                                                   @Parameter(description = "响应信息") MockResponse mockResponse) {
        try {
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);

            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }

            mockResponse.setMockApi(apiOpt.get());

            // 设置默认值
            if (mockResponse.getWeight() == null) {
                mockResponse.setWeight(100);
            }
            if (mockResponse.getEnabled() == null) {
                mockResponse.setEnabled(true);
            }

            MockResponse savedResponse = mockResponseRepository.save(mockResponse);

            // 如果设置为默认响应，确保同一接口、同一状态码下只有一个默认响应
            if (savedResponse.getIsDefault() != null && savedResponse.getIsDefault()) {
                ensureSingleDefaultResponse(savedResponse);
            }

            // 更新接口响应缓存
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
            cacheUtil.cacheApiResponses(apiId, responses);

            log.info("添加接口响应成功: 接口={}, 状态码={}", apiId, savedResponse.getStatusCode());
            return ApiResponse.success(savedResponse);

        } catch (Exception e) {
            log.error("添加接口响应失败: {}", e.getMessage(), e);
            return ApiResponse.error("添加接口响应失败，请稍后重试");
        }
    }

    /**
     * 更新接口响应
     *
     * @param mockResponse 响应信息
     * @return 更新的响应
     */
    @Operation(summary = "更新接口响应")
    @Transactional
    public ApiResponse<MockResponse> updateApiResponse(@Parameter(description = "响应信息") MockResponse mockResponse) {
        try {
            Optional<MockResponse> existingResponseOpt = mockResponseRepository.findById(mockResponse.getId());

            if (!existingResponseOpt.isPresent()) {
                return ApiResponse.error("响应不存在");
            }

            MockResponse existingResponse = existingResponseOpt.get();

            // 更新响应信息
            if (mockResponse.getStatusCode() != null) {
                existingResponse.setStatusCode(mockResponse.getStatusCode());
            }
            if (mockResponse.getContentType() != null) {
                existingResponse.setContentType(mockResponse.getContentType());
            }
            if (mockResponse.getHeaders() != null) {
                existingResponse.setHeaders(mockResponse.getHeaders());
            }
            if (mockResponse.getResponseBody() != null) {
                existingResponse.setResponseBody(mockResponse.getResponseBody());
            }
            if (mockResponse.getWeight() != null) {
                existingResponse.setWeight(mockResponse.getWeight());
            }
            if (mockResponse.getCondition() != null) {
                existingResponse.setCondition(mockResponse.getCondition());
            }
            if (mockResponse.getConditionDesc() != null) {
                existingResponse.setConditionDesc(mockResponse.getConditionDesc());
            }
            if (mockResponse.getEnabled() != null) {
                existingResponse.setEnabled(mockResponse.getEnabled());
            }
            if (mockResponse.getActive() != null) {
                existingResponse.setActive(mockResponse.getActive());
            }
            if (mockResponse.getIsDefault() != null) {
                existingResponse.setIsDefault(mockResponse.getIsDefault());
            }

            MockResponse updatedResponse = mockResponseRepository.save(existingResponse);

            // 如果设置为默认响应，确保同一接口、同一状态码下只有一个默认响应
            if (updatedResponse.getIsDefault() != null && updatedResponse.getIsDefault()) {
                ensureSingleDefaultResponse(updatedResponse);
            }

            // 更新接口响应缓存
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(existingResponse.getMockApi().getId());
            cacheUtil.cacheApiResponses(existingResponse.getMockApi().getId(), responses);

            log.info("更新接口响应成功: 响应ID={}", updatedResponse.getId());
            return ApiResponse.success(updatedResponse);

        } catch (Exception e) {
            log.error("更新接口响应失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新接口响应失败，请稍后重试");
        }
    }

    /**
     * 删除接口响应
     *
     * @param responseId 响应ID
     * @return 删除结果
     */
    @Operation(summary = "删除接口响应")
    @Transactional
    public ApiResponse<Void> deleteApiResponse(@Parameter(description = "响应ID", example = "1") Long responseId) {
        try {
            Optional<MockResponse> responseOpt = mockResponseRepository.findById(responseId);

            if (!responseOpt.isPresent()) {
                return ApiResponse.error("响应不存在");
            }

            MockResponse response = responseOpt.get();
            Long apiId = response.getMockApi().getId();

            mockResponseRepository.deleteById(responseId);

            // 更新接口响应缓存
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
            cacheUtil.cacheApiResponses(apiId, responses);

            log.info("删除接口响应成功: {}", responseId);
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("删除接口响应失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除接口响应失败，请稍后重试");
        }
    }

    /**
     * 设置接口的激活响应
     *
     * @param apiId      接口ID
     * @param responseId 响应ID
     * @return 操作结果
     */
    @Operation(summary = "设置接口的激活响应")
    @Transactional
    public ApiResponse<Void> setActiveResponse(@Parameter(description = "接口ID", example = "1") Long apiId,
                                              @Parameter(description = "响应ID", example = "1") Long responseId) {
        try {
            // 获取接口信息，判断是否启用了随机返回
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);
            if (!apiOpt.isPresent()) {
                return ApiResponse.error("接口不存在");
            }
            MockApi api = apiOpt.get();

            // 获取目标响应
            Optional<MockResponse> targetResponseOpt = mockResponseRepository.findById(responseId);
            if (!targetResponseOpt.isPresent()) {
                return ApiResponse.error("响应不存在");
            }
            MockResponse targetResponse = targetResponseOpt.get();

            if (api.getEnableRandom() != null && api.getEnableRandom()) {
                // 启用了随机返回，切换该响应的激活状态
                targetResponse.setActive(!targetResponse.getActive());
                mockResponseRepository.save(targetResponse);
                log.info("切换响应激活状态成功: 接口={}, 响应={}, 新状态={}", apiId, responseId, targetResponse.getActive());
            } else {
                // 未启用随机返回，只能有一个激活响应
                List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
                for (MockResponse response : responses) {
                    response.setActive(false);
                }
                mockResponseRepository.saveAll(responses);

                targetResponse.setActive(true);
                mockResponseRepository.save(targetResponse);
                log.info("设置激活响应成功: 接口={}, 响应={}", apiId, responseId);
            }

            // 更新接口响应缓存
            List<MockResponse> updatedResponses = mockResponseRepository.findByMockApiId(apiId);
            cacheUtil.cacheApiResponses(apiId, updatedResponses);

            return ApiResponse.success();

        } catch (Exception e) {
            log.error("设置激活响应失败: {}", e.getMessage(), e);
            return ApiResponse.error("设置激活响应失败，请稍后重试");
        }
    }

    /**
     * 确保同一接口、同一状态码下只有一个默认响应
     * 当某个响应被设置为默认响应时，取消其他相同状态码响应的默认标记
     *
     * @param response 当前被设置为默认的响应
     */
    private void ensureSingleDefaultResponse(MockResponse response) {
        Long apiId = response.getMockApi().getId();
        Integer statusCode = response.getStatusCode();
        List<MockResponse> sameStatusResponses = mockResponseRepository.findByMockApiIdAndStatusCode(apiId, statusCode);
        for (MockResponse other : sameStatusResponses) {
            if (other.getIsDefault() != null && other.getIsDefault() && !other.getId().equals(response.getId())) {
                other.setIsDefault(false);
                mockResponseRepository.save(other);
                log.info("取消其他响应的默认标记: 响应ID={}, 接口={}, 状态码={}", other.getId(), apiId, statusCode);
            }
        }
    }

    /**
     * 获取接口的所有响应
     *
     * @param apiId 接口ID
     * @return 响应列表
     */
    @Operation(summary = "获取接口的所有响应")
    public ApiResponse<List<MockResponse>> getApiResponses(@Parameter(description = "接口ID", example = "1") Long apiId) {
        try {
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
            return ApiResponse.success(responses);
        } catch (Exception e) {
            log.error("获取接口响应列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取接口响应列表失败，请稍后重试");
        }
    }
}
