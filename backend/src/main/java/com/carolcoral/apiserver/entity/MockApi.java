/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 自定义接口实体类
 * 用于管理API接口模拟系统中的接口信息
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-02
 */
@Schema(description = "自定义接口实体")
@Entity
@Table(name = "t_mock_api")
public class MockApi {

    @Schema(description = "接口ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "接口名称", example = "用户登录接口")
    @Column(nullable = false, length = 100)
    private String name;

    @Schema(description = "接口路径", example = "/api/user/login")
    @Column(nullable = false, length = 200)
    private String path;

    @Schema(description = "请求方法", example = "POST", allowableValues = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private HttpMethod method = HttpMethod.GET;

    @Schema(description = "请求类型", example = "HTTP", allowableValues = {"HTTP", "WEBSOCKET"})
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RequestType requestType = RequestType.HTTP;

    @Schema(description = "接口描述", example = "用户登录验证接口")
    @Column(length = 500)
    private String description;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    @Schema(description = "默认响应延迟（毫秒）", example = "100")
    @Column
    private Integer responseDelay;

    @Schema(description = "是否启用随机返回", example = "false")
    @Column(nullable = false)
    private Boolean enableRandom = false;

    @Schema(description = "自定义响应处理器类名", example = "TimestampAppender")
    @Column(length = 500)
    private String customResponseHandler;

    @Schema(description = "自定义响应处理器源码（动态编译模式）", example = "public class MyTransformer implements CustomResponseTransformer { ... }")
    @Column(columnDefinition = "TEXT")
    private String customResponseSource;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @Schema(description = "创建人ID", example = "1")
    @Column(nullable = false)
    private Long createUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @Schema(description = "所属项目")
    private Project project;

    @OneToMany(mappedBy = "mockApi", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("statusCode ASC")
    @Schema(description = "接口响应列表")
    private List<MockResponse> responses = new ArrayList<>();

    /**
     * 无参构造函数
     */
    public MockApi() {
    }

    /**
     * 全参构造函数
     *
     * @param id 接口ID
     * @param name 接口名称
     * @param path 接口路径
     * @param method 请求方法
     * @param requestType 请求类型
     * @param description 接口描述
     * @param enabled 是否启用
     * @param responseDelay 默认响应延迟（毫秒）
     * @param enableRandom 是否启用随机返回
     * @param customResponseHandler 自定义响应处理器类名
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @param createUserId 创建人ID
     * @param project 所属项目
     * @param responses 接口响应列表
     */
    public MockApi(Long id, String name, String path, HttpMethod method, RequestType requestType,
                   String description, Boolean enabled, Integer responseDelay, Boolean enableRandom,
                   String customResponseHandler,
                   LocalDateTime createTime, LocalDateTime updateTime, Long createUserId,
                   Project project, List<MockResponse> responses) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.method = method;
        this.requestType = requestType;
        this.description = description;
        this.enabled = enabled;
        this.responseDelay = responseDelay;
        this.enableRandom = enableRandom;
        this.customResponseHandler = customResponseHandler;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createUserId = createUserId;
        this.project = project;
        this.responses = responses;
    }

    /**
     * 获取接口ID
     *
     * @return 接口ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置接口ID
     *
     * @param id 接口ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取接口名称
     *
     * @return 接口名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置接口名称
     *
     * @param name 接口名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取接口路径
     *
     * @return 接口路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置接口路径
     *
     * @param path 接口路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取请求方法
     *
     * @return 请求方法
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * 设置请求方法
     *
     * @param method 请求方法
     */
    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    /**
     * 获取请求类型
     *
     * @return 请求类型
     */
    public RequestType getRequestType() {
        return requestType;
    }

    /**
     * 设置请求类型
     *
     * @param requestType 请求类型
     */
    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    /**
     * 获取接口描述
     *
     * @return 接口描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置接口描述
     *
     * @param description 接口描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取是否启用
     *
     * @return 是否启用
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认响应延迟（毫秒）
     *
     * @return 默认响应延迟（毫秒）
     */
    public Integer getResponseDelay() {
        return responseDelay;
    }

    /**
     * 设置默认响应延迟（毫秒）
     *
     * @param responseDelay 默认响应延迟（毫秒）
     */
    public void setResponseDelay(Integer responseDelay) {
        this.responseDelay = responseDelay;
    }

    /**
     * 获取是否启用随机返回
     *
     * @return 是否启用随机返回
     */
    public Boolean getEnableRandom() {
        return enableRandom;
    }

    /**
     * 设置是否启用随机返回
     *
     * @param enableRandom 是否启用随机返回
     */
    public void setEnableRandom(Boolean enableRandom) {
        this.enableRandom = enableRandom;
    }

    /**
     * 获取自定义响应处理器类名
     *
     * @return 自定义响应处理器类名
     */
    public String getCustomResponseHandler() {
        return customResponseHandler;
    }

    /**
     * 设置自定义响应处理器类名
     *
     * @param customResponseHandler 自定义响应处理器类名
     */
    public void setCustomResponseHandler(String customResponseHandler) {
        this.customResponseHandler = customResponseHandler;
    }

    /**
     * 获取自定义响应处理器源码
     *
     * @return 自定义响应处理器源码
     */
    public String getCustomResponseSource() {
        return customResponseSource;
    }

    /**
     * 设置自定义响应处理器源码
     *
     * @param customResponseSource 自定义响应处理器源码
     */
    public void setCustomResponseSource(String customResponseSource) {
        this.customResponseSource = customResponseSource;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取创建人ID
     *
     * @return 创建人ID
     */
    public Long getCreateUserId() {
        return createUserId;
    }

    /**
     * 设置创建人ID
     *
     * @param createUserId 创建人ID
     */
    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    /**
     * 获取所属项目
     *
     * @return 所属项目
     */
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "mockApis", "members"})
    public Project getProject() {
        return project;
    }

    /**
     * 设置所属项目
     *
     * @param project 所属项目
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * 获取接口响应列表
     *
     * @return 接口响应列表
     */
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    public List<MockResponse> getResponses() {
        return responses;
    }

    /**
     * 设置接口响应列表
     *
     * @param responses 接口响应列表
     */
    public void setResponses(List<MockResponse> responses) {
        this.responses = responses;
    }

    /**
     * 判断对象是否相等（基于ID）
     *
     * @param o 要比较的对象
     * @return 如果ID相同返回true，否则返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MockApi mockApi = (MockApi) o;
        return Objects.equals(id, mockApi.id);
    }

    /**
     * 计算哈希码（基于ID）
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 返回对象的字符串表示
     *
     * @return 对象的字符串表示
     */
    @Override
    public String toString() {
        return "MockApi{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", method=" + method +
                ", requestType=" + requestType +
                ", description='" + description + '\'' +
                ", enabled=" + enabled +
                ", responseDelay=" + responseDelay +
                ", enableRandom=" + enableRandom +
                ", customResponseHandler='" + customResponseHandler + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", createUserId=" + createUserId +
                ", project=" + project +
                ", responses=" + responses +
                '}';
    }

    /**
     * 持久化前回调方法
     * 设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /**
     * 更新前回调方法
     * 设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    /**
     * HTTP请求方法枚举
     */
    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }

    /**
     * 请求类型枚举
     */
    public enum RequestType {
        HTTP, WEBSOCKET
    }
}
