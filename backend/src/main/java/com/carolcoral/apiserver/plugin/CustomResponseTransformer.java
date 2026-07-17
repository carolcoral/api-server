/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.plugin;

import com.carolcoral.apiserver.dto.MockRequest;
import com.carolcoral.apiserver.dto.MockResponseDTO;

/**
 * 自定义响应转换器接口
 * <p>
 * 每个 Mock 接口（MockApi）可以独立配置一个专属的自定义报文处理方法。
 * 实现此接口后，即可在接口配置中通过页面提交 Java 源码进行动态编译和使用。
 * </p>
 *
 * <h3>执行时机</h3>
 * <p>
 * 该转换器在原有的响应匹配、延迟计算、响应体解析等基础功能完成之后执行，
 * 仅对最终返回的报文数据进行转换，不会干扰或覆盖原有功能。
 * </p>
 *
 * <h3>实现方式</h3>
 * <ul>
 *   <li><b>动态编译（推荐）</b> — 在管理页面直接编写 Java 代码，
 *       通过 {@link DynamicCompiler} 运行时编译并实例化，无需重启服务</li>
 *   <li><b>Spring Bean</b> — 实现此接口并注册为 Spring Bean，
 *       可在接口配置中通过类名引用（适用于固定业务逻辑）</li>
 * </ul>
 *
 * <h3>动态编译示例</h3>
 * <pre>{@code
 * public class MyTransformer implements CustomResponseTransformer {
 *     @Override
 *     public MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,
 *                                       String apiName, String apiPath) {
 *         Object body = mockResponse.getBody();
 *         Map<String, Object> result = new LinkedHashMap<>();
 *         result.put("code", 200);
 *         result.put("data", body);
 *         return MockResponseDTO.builder()
 *                 .statusCode(mockResponse.getStatusCode())
 *                 .headers(mockResponse.getHeaders())
 *                 .body(result)
 *                 .delay(mockResponse.getDelay())
 *                 .build();
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "标准格式包装器";
 *     }
 * }
 * }</pre>
 *
 * <h3>安全限制（动态编译模式）</h3>
 * <ul>
 *   <li>禁止使用 {@code java.lang.reflect} 反射 API</li>
 *   <li>禁止使用 {@code java.io.File}、{@code java.net.*} 等 IO/网络 API</li>
 *   <li>禁止使用 {@code java.lang.Runtime}、{@code java.lang.Process} 等进程 API</li>
 *   <li>禁止使用 {@code java.lang.Thread}、{@code java.lang.ClassLoader} 等线程/类加载 API</li>
 *   <li>禁止使用 {@code javax.script.*} 脚本执行 API</li>
 *   <li>代码长度不超过 50000 字符</li>
 * </ul>
 *
 * @author carolcoral
 * @see MockResponseDTO
 * @see MockRequest
 * @see DynamicCompiler
 */
public interface CustomResponseTransformer {

    /**
     * 对 Mock 响应进行自定义转换处理
     * <p>
     * 该方法在基础响应流程（响应匹配、延迟计算、响应体解析等）完成之后调用。
     * 可以在此方法中对响应体（body）、状态码（statusCode）、响应头（headers）等进行任意修改。
     * </p>
     *
     * <h3>典型应用场景</h3>
     * <ul>
     *   <li><b>响应包装</b> — 将原始响应包装为统一格式
     *       {@code {code, message, data, timestamp}}</li>
     *   <li><b>数据脱敏</b> — 对手机号、身份证等敏感字段进行掩码处理</li>
     *   <li><b>字段转换</b> — 修改字段名、类型转换、添加计算字段</li>
     *   <li><b>条件响应</b> — 根据请求参数动态生成不同的响应内容</li>
     *   <li><b>日志记录</b> — 记录请求/响应信息用于调试</li>
     * </ul>
     *
     * @param mockResponse 经过基础流程处理后的 Mock 响应对象
     *                     <ul>
     *                       <li>{@code getStatusCode()} — HTTP 状态码（如 200, 404, 500）</li>
     *                       <li>{@code getHeaders()} — 响应头键值对 Map</li>
     *                       <li>{@code getBody()} — 响应体，可能是 String、Map 或 List</li>
     *                       <li>{@code getDelay()} — 响应延迟（毫秒），null 表示无延迟</li>
     *                     </ul>
     * @param mockRequest  原始 Mock 请求对象
     *                     <ul>
     *                       <li>{@code getPath()} — 请求路径，如 "/api/user/login"</li>
     *                       <li>{@code getMethod()} — 请求方法（GET/POST/PUT/DELETE）</li>
     *                       <li>{@code getHeaders()} — 请求头键值对 Map</li>
     *                       <li>{@code getParams()} — URL 查询参数键值对 Map</li>
     *                       <li>{@code getBody()} — 请求体内容</li>
     *                       <li>{@code getProjectCode()} — 项目编码</li>
     *                       <li>{@code getPathParams()} — RESTful 路径参数 Map</li>
     *                     </ul>
     * @param apiName      接口名称，可用于日志记录和条件判断
     * @param apiPath      接口路径，可用于日志记录和条件判断
     * @return 转换后的 Mock 响应对象，不能返回 {@code null}
     * @see MockResponseDTO#builder()
     */
    MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest,
                              String apiName, String apiPath);

    /**
     * 获取转换器名称
     * <p>用于在管理界面展示，帮助区分不同的处理器实例。</p>
     *
     * @return 转换器名称，默认为类名
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取转换器描述
     * <p>用于在管理界面展示处理器的功能说明。建议使用简短的中文描述，
     * 如"标准格式包装器"、"数据脱敏处理器"等。</p>
     *
     * @return 转换器描述字符串，默认为空字符串
     */
    default String getDescription() {
        return "";
    }
}
