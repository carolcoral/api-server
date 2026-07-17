/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存工具类
 *
 * @author carolcoral
 */
@Tag(name = "缓存工具", description = "缓存管理工具类")
@Component
public class CacheUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CacheUtil.class);

    private static final String PROJECT_CACHE = "projects";
    private static final String API_CACHE = "apis";
    private static final String RESPONSE_CACHE = "responses";
    private static final String PROJECT_API_CACHE = "project_apis";

    private final CacheManager cacheManager;
    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;
    private final MockResponseRepository mockResponseRepository;

    /**
     * 构造器
     */
    public CacheUtil(CacheManager cacheManager, ProjectRepository projectRepository,
                      MockApiRepository mockApiRepository, MockResponseRepository mockResponseRepository) {
        this.cacheManager = cacheManager;
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.mockResponseRepository = mockResponseRepository;
    }

    // 内存缓存 - 用于高速访问
    private final ConcurrentHashMap<String, Project> projectCodeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MockApi> apiPathCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<MockResponse>> apiResponsesCache = new ConcurrentHashMap<>();

    @PostConstruct
    @Operation(summary = "初始化缓存", description = "应用启动时加载所有数据到缓存")
    public void initCache() {
        log.info("开始初始化缓存...");
        try {
            // 加载所有项目
            List<Project> projects = projectRepository.findAll();
            projects.forEach(this::cacheProject);
            log.info("加载 {} 个项目到缓存", projects.size());

            // 加载所有接口
            List<MockApi> apis = mockApiRepository.findAll();
            apis.forEach(this::cacheApi);
            log.info("加载 {} 个接口到缓存", apis.size());

            // 加载所有响应
            apis.forEach(api -> {
                List<MockResponse> responses = mockResponseRepository.findByMockApiId(api.getId());
                cacheApiResponses(api.getId(), responses);
            });
            log.info("加载所有接口响应到缓存");

            log.info("缓存初始化完成");
        } catch (Exception e) {
            log.error("缓存初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 缓存项目
     *
     * @param project 项目
     */
    @Operation(summary = "缓存项目")
    public void cacheProject(Project project) {
        if (project != null && project.getCode() != null) {
            projectCodeCache.put(project.getCode(), project);
            putCache(PROJECT_CACHE, project.getId(), project);
            log.debug("缓存项目: {}", project.getCode());
        }
    }

    /**
     * 从缓存获取项目
     *
     * @param code 项目编码
     * @return 项目Optional
     */
    @Operation(summary = "从缓存获取项目")
    public Optional<Project> getProjectFromCache(String code) {
        Project project = projectCodeCache.get(code);
        if (project != null) {
            log.debug("从缓存获取项目: {}", code);
            return Optional.of(project);
        }

        // 从数据库查询并缓存
        Optional<Project> projectOpt = projectRepository.findByCode(code);
        projectOpt.ifPresent(this::cacheProject);
        return projectOpt;
    }

    /**
     * 缓存接口
     *
     * @param api 接口
     */
    @Operation(summary = "缓存接口")
    public void cacheApi(MockApi api) {
        if (api != null && api.getPath() != null) {
            // 缓存不带项目ID的键（用于全局匹配）
            String cacheKey = buildApiCacheKey(api.getPath(), api.getMethod());
            apiPathCache.put(cacheKey, api);
            putCache(API_CACHE, api.getId(), api);
            log.debug("缓存接口: {}", cacheKey);

            // 同时缓存带项目ID的键（用于精确匹配，确保更新后请求处理器能命中最新数据）
            if (api.getProject() != null) {
                String projectKey = buildApiCacheKey(api.getProject().getId(), api.getPath(), api.getMethod());
                apiPathCache.put(projectKey, api);
                putCache(API_CACHE, projectKey, api);
                log.debug("缓存接口（含项目ID）: {}", projectKey);
            }
        }
    }

    /**
     * 从缓存获取接口
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 接口Optional
     */
    @Operation(summary = "从缓存获取接口")
    public Optional<MockApi> getApiFromCache(String path, MockApi.HttpMethod method) {
        String cacheKey = buildApiCacheKey(path, method);
        MockApi api = apiPathCache.get(cacheKey);
        if (api != null) {
            log.debug("从缓存获取接口: {}", cacheKey);
            return Optional.of(api);
        }

        // 从数据库查询并缓存
        Optional<MockApi> apiOpt = mockApiRepository.findByPathAndMethod(path, method);
        apiOpt.ifPresent(this::cacheApi);
        return apiOpt;
    }

    /**
     * 从缓存获取接口（根据项目ID）
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @param method    请求方法
     * @return 接口Optional
     */
    @Operation(summary = "从缓存获取接口（根据项目ID）")
    public Optional<MockApi> getApiFromCache(Long projectId, String path, MockApi.HttpMethod method) {
        // 构建包含项目ID的缓存键
        String cacheKey = buildApiCacheKey(projectId, path, method);
        MockApi cachedApi = apiPathCache.get(cacheKey);
        if (cachedApi != null) {
            log.debug("从缓存获取接口（项目ID={}）: {}", projectId, cacheKey);
            return Optional.of(cachedApi);
        }

        // 从数据库查询并缓存
        Optional<MockApi> apiOpt = mockApiRepository.findByProjectIdAndPathAndMethod(projectId, path, method);
        apiOpt.ifPresent(api -> {
            cacheApiWithProject(api, projectId);
        });
        return apiOpt;
    }

    /**
     * 从缓存获取接口（根据路径）
     *
     * @param path 接口路径
     * @return 接口Optional
     */
    @Operation(summary = "从缓存获取接口（根据路径）")
    public Optional<MockApi> getApiFromCache(String path) {
        // 遍历所有方法类型
        for (MockApi.HttpMethod method : MockApi.HttpMethod.values()) {
            Optional<MockApi> apiOpt = getApiFromCache(path, method);
            if (apiOpt.isPresent()) {
                return apiOpt;
            }
        }
        return Optional.empty();
    }

    /**
     * 缓存接口响应
     *
     * @param apiId    接口ID
     * @param responses 响应列表
     */
    @Operation(summary = "缓存接口响应")
    public void cacheApiResponses(Long apiId, List<MockResponse> responses) {
        if (apiId != null && responses != null) {
            apiResponsesCache.put(apiId, responses);
            putCache(RESPONSE_CACHE, apiId, responses);
            log.debug("缓存接口 {} 的 {} 个响应", apiId, responses.size());
        }
    }

    /**
     * 从缓存获取接口响应
     *
     * @param apiId 接口ID
     * @return 响应列表
     */
    @Operation(summary = "从缓存获取接口响应")
    public List<MockResponse> getApiResponsesFromCache(Long apiId) {
        List<MockResponse> responses = apiResponsesCache.get(apiId);
        if (responses != null) {
            log.debug("从缓存获取接口 {} 的 {} 个响应", apiId, responses.size());
            return responses;
        }

        // 从数据库查询并缓存
        responses = mockResponseRepository.findByMockApiId(apiId);
        cacheApiResponses(apiId, responses);
        return responses;
    }

    /**
     * 缓存项目下的所有接口
     *
     * @param projectId 项目ID
     * @param apis      接口列表
     */
    @Operation(summary = "缓存项目下的所有接口")
    public void cacheProjectApis(Long projectId, List<MockApi> apis) {
        if (projectId != null && apis != null) {
            putCache(PROJECT_API_CACHE, projectId, apis);
            log.debug("缓存项目 {} 的 {} 个接口", projectId, apis.size());
        }
    }

    /**
     * 从缓存获取项目下的接口
     *
     * @param projectId 项目ID
     * @return 接口列表
     */
    @Operation(summary = "从缓存获取项目下的接口")
    public List<MockApi> getProjectApisFromCache(Long projectId) {
        return getCache(PROJECT_API_CACHE, projectId);
    }

    /**
     * 清除项目缓存
     *
     * @param projectId 项目ID
     */
    @Operation(summary = "清除项目缓存")
    public void evictProjectCache(Long projectId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            projectCodeCache.remove(project.getCode());
            evictCache(PROJECT_CACHE, projectId);
            evictCache(PROJECT_API_CACHE, projectId);
            log.debug("清除项目缓存: {}", project.getCode());
        }
    }

    /**
     * 清除接口缓存
     *
     * @param apiId 接口ID
     */
    @Operation(summary = "清除接口缓存")
    public void evictApiCache(Long apiId) {
        Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);
        if (apiOpt.isPresent()) {
            MockApi api = apiOpt.get();
            // 清除不带项目ID的键
            String cacheKey = buildApiCacheKey(api.getPath(), api.getMethod());
            apiPathCache.remove(cacheKey);
            // 清除带项目ID的键
            if (api.getProject() != null) {
                String projectKey = buildApiCacheKey(api.getProject().getId(), api.getPath(), api.getMethod());
                apiPathCache.remove(projectKey);
            }
            evictCache(API_CACHE, apiId);
            evictApiResponsesCache(apiId);
            log.debug("清除接口缓存: {}", cacheKey);
        }
    }

    /**
     * 清除接口响应缓存
     *
     * @param apiId 接口ID
     */
    @Operation(summary = "清除接口响应缓存")
    public void evictApiResponsesCache(Long apiId) {
        apiResponsesCache.remove(apiId);
        evictCache(RESPONSE_CACHE, apiId);
        log.debug("清除接口 {} 的响应缓存", apiId);
    }

    /**
     * 清除所有缓存
     */
    @Operation(summary = "清除所有缓存")
    public void evictAllCache() {
        projectCodeCache.clear();
        apiPathCache.clear();
        apiResponsesCache.clear();

        Cache projectCache = cacheManager.getCache(PROJECT_CACHE);
        if (projectCache != null) {
            projectCache.clear();
        }

        Cache apiCache = cacheManager.getCache(API_CACHE);
        if (apiCache != null) {
            apiCache.clear();
        }

        Cache responseCache = cacheManager.getCache(RESPONSE_CACHE);
        if (responseCache != null) {
            responseCache.clear();
        }

        Cache projectApiCache = cacheManager.getCache(PROJECT_API_CACHE);
        if (projectApiCache != null) {
            projectApiCache.clear();
        }

        log.info("清除所有缓存完成");
    }

    /**
     * 构建接口缓存Key
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 缓存Key
     */
    @Operation(summary = "构建接口缓存Key")
    private String buildApiCacheKey(String path, MockApi.HttpMethod method) {
        return path + ":" + method.name();
    }

    /**
     * 构建接口缓存Key（包含项目ID）
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @param method    请求方法
     * @return 缓存Key
     */
    @Operation(summary = "构建接口缓存Key（包含项目ID）")
    private String buildApiCacheKey(Long projectId, String path, MockApi.HttpMethod method) {
        return projectId + ":" + path + ":" + method.name();
    }

    /**
     * 缓存接口（包含项目ID）
     *
     * @param api       Mock接口
     * @param projectId 项目ID
     */
    @Operation(summary = "缓存接口（包含项目ID）")
    private void cacheApiWithProject(MockApi api, Long projectId) {
        String cacheKey = buildApiCacheKey(projectId, api.getPath(), api.getMethod());
        apiPathCache.put(cacheKey, api);
        putCache(API_CACHE, cacheKey, api);
        log.debug("缓存接口（项目ID={}）: {}", projectId, cacheKey);
    }

    /**
     * 从Spring Cache获取数据
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param <T>       数据类型
     * @return 数据
     */
    @Operation(summary = "从Spring Cache获取数据")
    private <T> T getCache(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                return (T) wrapper.get();
            }
        }
        return null;
    }

    /**
     * 向Spring Cache添加数据
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param value     数据
     */
    @Operation(summary = "向Spring Cache添加数据")
    private void putCache(String cacheName, Object key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    /**
     * 清除Spring Cache中的数据
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     */
    @Operation(summary = "清除Spring Cache中的数据")
    private void evictCache(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    /**
     * 获取项目编码缓存大小
     *
     * @return 缓存大小
     */
    @Operation(summary = "获取项目编码缓存大小")
    public int getProjectCacheSize() {
        return projectCodeCache.size();
    }

    /**
     * 获取接口路径缓存大小
     *
     * @return 缓存大小
     */
    @Operation(summary = "获取接口路径缓存大小")
    public int getApiCacheSize() {
        return apiPathCache.size();
    }

    /**
     * 获取接口响应缓存大小
     *
     * @return 缓存大小
     */
    @Operation(summary = "获取接口响应缓存大小")
    public int getApiResponsesCacheSize() {
        return apiResponsesCache.size();
    }
}
