/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.plugin;

import com.carolcoral.apiserver.dto.MockRequest;
import com.carolcoral.apiserver.dto.MockResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义响应转换器注册中心
 * <p>
 * 负责管理所有注册的CustomResponseTransformer实现，支持三种来源：
 * 1. Spring Bean（内置转换器，如ResponseWrapperTransformer等）
 * 2. 动态编译（用户通过页面提交的Java源码）
 * 3. 全限定类名引用
 * </p>
 * <p>
 * 查找优先级：动态编译缓存 > Spring Bean > 全限定类名
 * </p>
 *
 * @author carolcoral
 */
@Component
public class TransformerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TransformerRegistry.class);

    private final ApplicationContext applicationContext;

    /**
     * 转换器缓存：className -> transformer实例
     */
    private final ConcurrentHashMap<String, CustomResponseTransformer> transformerCache = new ConcurrentHashMap<>();

    public TransformerRegistry(@Autowired ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 根据转换器类名获取转换器实例
     *
     * @param className 转换器类名（支持simple name或全限定名）
     * @return 转换器实例，如果未找到则返回null
     */
    public CustomResponseTransformer getTransformer(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }

        String name = className.trim();

        // 先从缓存中获取
        CustomResponseTransformer transformer = transformerCache.get(name);
        if (transformer != null) {
            return transformer;
        }

        // 尝试通过Spring容器查找
        try {
            Class<?> clazz = Class.forName(name);
            if (CustomResponseTransformer.class.isAssignableFrom(clazz)) {
                Map<String, ?> beans = applicationContext.getBeansOfType(clazz);
                if (!beans.isEmpty()) {
                    transformer = (CustomResponseTransformer) beans.values().iterator().next();
                    transformerCache.put(name, transformer);
                    log.info("通过全限定名加载转换器: {}", name);
                    return transformer;
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("未找到全限定名对应的类: {}, 尝试通过Bean名称查找", name);
        }

        // 尝试通过Spring Bean名称查找（支持simple name）
        try {
            String beanName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            if (applicationContext.containsBean(beanName)) {
                Object bean = applicationContext.getBean(beanName);
                if (bean instanceof CustomResponseTransformer) {
                    transformer = (CustomResponseTransformer) bean;
                    transformerCache.put(name, transformer);
                    log.info("通过Bean名称加载转换器: {}", name);
                    return transformer;
                }
            }
        } catch (Exception e) {
            log.debug("通过Bean名称查找转换器失败: {}", e.getMessage());
        }

        // 遍历所有CustomResponseTransformer类型的Bean进行匹配
        try {
            Map<String, CustomResponseTransformer> allTransformers =
                    applicationContext.getBeansOfType(CustomResponseTransformer.class);
            for (CustomResponseTransformer t : allTransformers.values()) {
                if (t.getClass().getSimpleName().equals(name) || t.getClass().getName().equals(name)) {
                    transformerCache.put(name, t);
                    log.info("通过遍历匹配加载转换器: {}", name);
                    return t;
                }
            }
        } catch (Exception e) {
            log.debug("遍历查找转换器失败: {}", e.getMessage());
        }

        log.warn("未找到转换器: {}", name);
        return null;
    }

    /**
     * 使用动态编译的源码执行响应转换
     *
     * @param apiId        接口ID
     * @param sourceCode   用户提交的Java源码
     * @param mockResponse 原始响应
     * @param mockRequest  原始请求
     * @param apiName      接口名称
     * @param apiPath      接口路径
     * @return 转换后的响应
     */
    public MockResponseDTO transformWithSource(Long apiId, String sourceCode,
                                                MockResponseDTO mockResponse, MockRequest mockRequest,
                                                String apiName, String apiPath) {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return mockResponse;
        }

        try {
            // 动态编译并获取实例
            CustomResponseTransformer transformer = DynamicCompiler.compileAndInstantiate(apiId, sourceCode);
            if (transformer == null) {
                log.warn("动态编译返回null，使用原始响应: apiId={}", apiId);
                return mockResponse;
            }

            log.info("执行动态自定义响应转换: apiId={}, apiName={}", apiId, apiName);
            MockResponseDTO result = transformer.transform(mockResponse, mockRequest, apiName, apiPath);
            if (result == null) {
                log.warn("动态转换器返回null，使用原始响应: apiId={}", apiId);
                return mockResponse;
            }
            log.info("动态自定义响应转换完成: apiId={}, apiName={}", apiId, apiName);
            return result;
        } catch (DynamicCompiler.CompilationException e) {
            log.error("动态编译失败: apiId={}, 错误={}", apiId, e.getMessage());
            // 编译失败时返回原始响应，确保不影响正常功能
            return mockResponse;
        } catch (Exception e) {
            log.error("动态转换执行失败: apiId={}, 错误={}", apiId, e.getMessage(), e);
            return mockResponse;
        }
    }

    /**
     * 获取所有已注册的转换器信息（用于管理界面展示）
     *
     * @return 转换器名称 -> 描述 的映射
     */
    public Map<String, String> getAllTransformers() {
        Map<String, String> result = new ConcurrentHashMap<>();
        try {
            Map<String, CustomResponseTransformer> allTransformers =
                    applicationContext.getBeansOfType(CustomResponseTransformer.class);
            for (CustomResponseTransformer t : allTransformers.values()) {
                result.put(t.getClass().getSimpleName(), t.getDescription());
            }
        } catch (Exception e) {
            log.warn("获取所有转换器失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 检查转换器是否存在
     *
     * @param className 转换器类名
     * @return 是否存在
     */
    public boolean hasTransformer(String className) {
        return getTransformer(className) != null;
    }

    /**
     * 执行自定义响应转换（通过类名引用Spring Bean转换器）
     *
     * @param className     转换器类名
     * @param mockResponse  原始响应
     * @param mockRequest   原始请求
     * @param apiName       接口名称
     * @param apiPath       接口路径
     * @return 转换后的响应
     */
    public MockResponseDTO transform(String className, MockResponseDTO mockResponse,
                                      MockRequest mockRequest, String apiName, String apiPath) {
        if (className == null || className.trim().isEmpty()) {
            return mockResponse;
        }

        CustomResponseTransformer transformer = getTransformer(className);
        if (transformer == null) {
            log.warn("转换器不存在，返回原始响应: {}", className);
            return mockResponse;
        }

        try {
            log.info("执行自定义响应转换: 接口={}, 转换器={}", apiName, className);
            MockResponseDTO result = transformer.transform(mockResponse, mockRequest, apiName, apiPath);
            if (result == null) {
                log.warn("转换器返回null，使用原始响应: {}", className);
                return mockResponse;
            }
            log.info("自定义响应转换完成: 接口={}, 转换器={}", apiName, className);
            return result;
        } catch (Exception e) {
            log.error("自定义响应转换失败: 接口={}, 转换器={}, 错误={}", apiName, className, e.getMessage(), e);
            return mockResponse;
        }
    }

    /**
     * 清除动态编译缓存
     */
    public void evictDynamicCache(Long apiId) {
        DynamicCompiler.evictCache(apiId);
    }

    /**
     * 获取缓存的动态源码
     */
    public String getCachedSourceCode(Long apiId) {
        return DynamicCompiler.getCachedSource(apiId);
    }

    /**
     * 编译验证源码（不执行转换，仅验证编译是否通过）
     *
     * @param apiId      接口ID（用于缓存隔离）
     * @param sourceCode Java源码
     * @return 编译错误信息，null表示编译成功
     */
    public String validateSourceCode(Long apiId, String sourceCode) {
        try {
            DynamicCompiler.compileAndInstantiate(apiId, sourceCode);
            return null; // 编译成功
        } catch (DynamicCompiler.CompilationException e) {
            return e.getMessage();
        }
    }
}
