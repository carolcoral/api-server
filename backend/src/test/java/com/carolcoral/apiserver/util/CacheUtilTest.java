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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * CacheUtil 单元测试：缓存读写、失效与回源逻辑
 *
 * @author carolcoral
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheUtilTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MockApiRepository mockApiRepository;

    @Mock
    private MockResponseRepository mockResponseRepository;

    private CacheUtil cacheUtil;

    /** 使用真实 CacheManager，保证 put/get/evict 行为可验证 */
    private final CacheManager cacheManager = new ConcurrentMapCacheManager(
            "projects", "apis", "responses", "project_apis");

    @BeforeEach
    void setUp() {
        cacheUtil = new CacheUtil(cacheManager, projectRepository, mockApiRepository, mockResponseRepository);
    }

    private Project project(Long id, String code) {
        Project p = new Project();
        p.setId(id);
        p.setCode(code);
        p.setName("项目" + id);
        return p;
    }

    private MockApi api(Long id, Project project, String path, MockApi.HttpMethod method) {
        MockApi a = new MockApi();
        a.setId(id);
        a.setProject(project);
        a.setPath(path);
        a.setMethod(method);
        return a;
    }

    @Nested
    @DisplayName("项目缓存")
    class ProjectCache {

        @Test
        @DisplayName("缓存后可从缓存直接命中，不回源数据库")
        void cacheThenHit() {
            cacheUtil.cacheProject(project(1L, "demo"));

            Optional<Project> found = cacheUtil.getProjectFromCache("demo");
            assertTrue(found.isPresent());
            assertEquals(1L, found.get().getId());
            assertEquals(1, cacheUtil.getProjectCacheSize());
        }

        @Test
        @DisplayName("缓存未命中时回源数据库并写入缓存")
        void missFallsBackToRepository() {
            when(projectRepository.findByCode("db-only")).thenReturn(Optional.of(project(2L, "db-only")));

            Optional<Project> found = cacheUtil.getProjectFromCache("db-only");

            assertTrue(found.isPresent());
            assertEquals(2L, found.get().getId());
            // 第二次命中缓存
            assertTrue(cacheUtil.getProjectFromCache("db-only").isPresent());
        }

        @Test
        @DisplayName("数据库中不存在时返回空 Optional")
        void missWithoutData() {
            when(projectRepository.findByCode("missing")).thenReturn(Optional.empty());
            assertTrue(cacheUtil.getProjectFromCache("missing").isEmpty());
        }

        @Test
        @DisplayName("code 为 null 的项目不入缓存")
        void nullCodeIsIgnored() {
            cacheUtil.cacheProject(project(3L, null));
            cacheUtil.cacheProject(null);
            assertEquals(0, cacheUtil.getProjectCacheSize());
        }

        @Test
        @DisplayName("清除项目缓存同时清理项目编码与项目接口缓存")
        void evictProjectCache() {
            Project p = project(1L, "demo");
            cacheUtil.cacheProject(p);
            cacheUtil.cacheProjectApis(1L, List.of(api(10L, p, "/a", MockApi.HttpMethod.GET)));
            when(projectRepository.findById(1L)).thenReturn(Optional.of(p));

            cacheUtil.evictProjectCache(1L);

            assertEquals(0, cacheUtil.getProjectCacheSize());
            assertNull(cacheUtil.getProjectApisFromCache(1L));
        }

        @Test
        @DisplayName("项目不存在时清除操作安全返回")
        void evictMissingProjectIsNoop() {
            when(projectRepository.findById(anyLong())).thenReturn(Optional.empty());
            assertDoesNotThrow(() -> cacheUtil.evictProjectCache(99L));
        }
    }

    @Nested
    @DisplayName("接口缓存")
    class ApiCache {

        @Test
        @DisplayName("缓存接口后可按键命中，且含项目ID的键同时写入")
        void cacheThenHit() {
            Project p = project(1L, "demo");
            cacheUtil.cacheApi(api(10L, p, "/users", MockApi.HttpMethod.GET));

            assertTrue(cacheUtil.getApiFromCache("/users", MockApi.HttpMethod.GET).isPresent());
            assertTrue(cacheUtil.getApiFromCache(1L, "/users", MockApi.HttpMethod.GET).isPresent());
            assertEquals(2, cacheUtil.getApiCacheSize());
        }

        @Test
        @DisplayName("无归属项目的接口只缓存全局键")
        void apiWithoutProject() {
            cacheUtil.cacheApi(api(11L, null, "/health", MockApi.HttpMethod.GET));
            assertEquals(1, cacheUtil.getApiCacheSize());
            assertTrue(cacheUtil.getApiFromCache("/health", MockApi.HttpMethod.GET).isPresent());
        }

        @Test
        @DisplayName("path 为 null 的接口不入缓存")
        void nullPathIsIgnored() {
            cacheUtil.cacheApi(api(12L, null, null, MockApi.HttpMethod.GET));
            cacheUtil.cacheApi(null);
            assertEquals(0, cacheUtil.getApiCacheSize());
        }

        @Test
        @DisplayName("缓存未命中时回源数据库")
        void missFallsBackToRepository() {
            MockApi fromDb = api(20L, null, "/db", MockApi.HttpMethod.POST);
            when(mockApiRepository.findByPathAndMethod("/db", MockApi.HttpMethod.POST)).thenReturn(Optional.of(fromDb));

            Optional<MockApi> found = cacheUtil.getApiFromCache("/db", MockApi.HttpMethod.POST);

            assertTrue(found.isPresent());
            assertEquals(20L, found.get().getId());
        }

        @Test
        @DisplayName("无项目ID的查询在任意方法命中时返回该接口")
        void getByPathScansAllMethods() {
            cacheUtil.cacheApi(api(30L, null, "/scan", MockApi.HttpMethod.DELETE));
            Optional<MockApi> found = cacheUtil.getApiFromCache("/scan");
            assertTrue(found.isPresent());
            assertEquals(30L, found.get().getId());
        }

        @Test
        @DisplayName("无项目ID的查询全部未命中时返回空")
        void getByPathMissReturnsEmpty() {
            assertTrue(cacheUtil.getApiFromCache("/nothing").isEmpty());
        }

        @Test
        @DisplayName("按项目ID未命中时回源并写入含项目ID的键")
        void missWithProjectIdCachesScopedKey() {
            Project p = project(5L, "p5");
            MockApi fromDb = api(40L, p, "/scoped", MockApi.HttpMethod.PUT);
            when(mockApiRepository.findByProjectIdAndPathAndMethod(5L, "/scoped", MockApi.HttpMethod.PUT))
                    .thenReturn(Optional.of(fromDb));

            Optional<MockApi> found = cacheUtil.getApiFromCache(5L, "/scoped", MockApi.HttpMethod.PUT);

            assertTrue(found.isPresent());
            assertEquals(40L, found.get().getId());
        }

        @Test
        @DisplayName("清除接口缓存同时移除全局键与项目键")
        void evictApiCache() {
            Project p = project(1L, "demo");
            MockApi a = api(50L, p, "/evict", MockApi.HttpMethod.GET);
            cacheUtil.cacheApi(a);
            when(mockApiRepository.findById(50L)).thenReturn(Optional.of(a));

            cacheUtil.evictApiCache(50L);

            assertTrue(cacheUtil.getApiFromCache(1L, "/evict", MockApi.HttpMethod.GET).isEmpty());
        }

        @Test
        @DisplayName("接口不存在时清除操作安全返回")
        void evictMissingApiIsNoop() {
            when(mockApiRepository.findById(anyLong())).thenReturn(Optional.empty());
            assertDoesNotThrow(() -> cacheUtil.evictApiCache(404L));
        }
    }

    @Nested
    @DisplayName("响应缓存")
    class ResponseCache {

        @Test
        @DisplayName("缓存后可命中")
        void cacheThenHit() {
            List<MockResponse> responses = List.of(new MockResponse(), new MockResponse());
            cacheUtil.cacheApiResponses(1L, responses);
            assertEquals(2, cacheUtil.getApiResponsesFromCache(1L).size());
            assertEquals(1, cacheUtil.getApiResponsesCacheSize());
        }

        @Test
        @DisplayName("未命中时回源数据库")
        void missFallsBackToRepository() {
            when(mockResponseRepository.findByMockApiId(7L)).thenReturn(List.of(new MockResponse()));
            assertEquals(1, cacheUtil.getApiResponsesFromCache(7L).size());
        }

        @Test
        @DisplayName("apiId 或响应列表为空时不缓存")
        void nullArgsAreIgnored() {
            cacheUtil.cacheApiResponses(null, List.of(new MockResponse()));
            cacheUtil.cacheApiResponses(1L, null);
            assertEquals(0, cacheUtil.getApiResponsesCacheSize());
        }

        @Test
        @DisplayName("清除单个接口的响应缓存")
        void evictApiResponses() {
            cacheUtil.cacheApiResponses(8L, List.of(new MockResponse()));
            cacheUtil.evictApiResponsesCache(8L);
            assertEquals(0, cacheUtil.getApiResponsesCacheSize());
        }
    }

    @Nested
    @DisplayName("项目接口缓存与全局清理")
    class ProjectApisAndEvictAll {

        @Test
        @DisplayName("缓存项目接口后可读回，参数非法时不缓存")
        void cacheAndRead() {
            cacheUtil.cacheProjectApis(1L, List.of(api(1L, null, "/x", MockApi.HttpMethod.GET)));
            assertEquals(1, cacheUtil.getProjectApisFromCache(1L).size());

            cacheUtil.cacheProjectApis(null, List.of());
            cacheUtil.cacheProjectApis(2L, null);
            assertNull(cacheUtil.getProjectApisFromCache(2L));
            assertNull(cacheUtil.getProjectApisFromCache(999L));
        }

        @Test
        @DisplayName("evictAllCache 清空全部内存与 Spring Cache")
        void evictAll() {
            Project p = project(1L, "demo");
            cacheUtil.cacheProject(p);
            cacheUtil.cacheApi(api(1L, p, "/a", MockApi.HttpMethod.GET));
            cacheUtil.cacheApiResponses(1L, List.of(new MockResponse()));
            cacheUtil.cacheProjectApis(1L, List.of());

            cacheUtil.evictAllCache();

            assertEquals(0, cacheUtil.getProjectCacheSize());
            assertEquals(0, cacheUtil.getApiCacheSize());
            assertEquals(0, cacheUtil.getApiResponsesCacheSize());
            assertNull(cacheUtil.getProjectApisFromCache(1L));
        }

        @Test
        @DisplayName("CacheManager 返回 null 时缓存操作不抛异常")
        void nullCacheIsTolerated() {
            CacheManager empty = new CacheManager() {
                @Override
                public Cache getCache(String name) {
                    return null;
                }

                @Override
                public java.util.Collection<String> getCacheNames() {
                    return List.of();
                }
            };
            CacheUtil util = new CacheUtil(empty, projectRepository, mockApiRepository, mockResponseRepository);
            assertDoesNotThrow(() -> {
                util.cacheProject(project(1L, "c"));
                util.cacheApi(api(1L, null, "/p", MockApi.HttpMethod.GET));
                util.cacheApiResponses(1L, List.of());
                util.evictApiResponsesCache(1L);
                util.evictAllCache();
            });
        }
    }

    @Nested
    @DisplayName("缓存初始化")
    class InitCache {

        @Test
        @DisplayName("启动时全量加载项目、接口与响应")
        void initLoadsEverything() {
            Project p = project(1L, "demo");
            MockApi a = api(10L, p, "/init", MockApi.HttpMethod.GET);
            when(projectRepository.findAll()).thenReturn(List.of(p));
            when(mockApiRepository.findAll()).thenReturn(List.of(a));
            when(mockResponseRepository.findByMockApiId(any())).thenReturn(List.of(new MockResponse()));

            cacheUtil.initCache();

            assertEquals(1, cacheUtil.getProjectCacheSize());
            assertEquals(2, cacheUtil.getApiCacheSize());
            assertEquals(1, cacheUtil.getApiResponsesCacheSize());
        }

        @Test
        @DisplayName("仓储抛异常时初始化失败不影响启动")
        void initFailureIsCaught() {
            when(projectRepository.findAll()).thenThrow(new RuntimeException("db down"));
            assertDoesNotThrow(() -> cacheUtil.initCache());
        }
    }
}
