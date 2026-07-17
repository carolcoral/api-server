/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目实体类
 * 用于管理API接口模拟系统中的项目信息
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-02
 */
@Schema(description = "项目实体")
@Entity
@Table(name = "t_project")
public class Project {

    @Schema(description = "项目ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "项目名称", example = "电商平台API")
    @Column(nullable = false, length = 100)
    private String name;

    @Schema(description = "项目描述", example = "电商平台的API接口模拟")
    @Column(length = 500)
    private String description;

    @Schema(description = "项目编码（唯一）", example = "ecmall")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @Schema(description = "创建人ID", example = "1")
    @Column(nullable = false)
    private Long createUserId;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Schema(description = "项目下的API列表")
    private List<MockApi> mockApis = new ArrayList<>();

    /**
     * 无参构造函数
     */
    public Project() {
    }

    /**
     * 全参构造函数
     *
     * @param id 项目ID
     * @param name 项目名称
     * @param description 项目描述
     * @param code 项目编码
     * @param enabled 是否启用
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @param createUserId 创建人ID
     * @param mockApis 项目下的API列表
     */
    public Project(Long id, String name, String description, String code, Boolean enabled,
                   LocalDateTime createTime, LocalDateTime updateTime, Long createUserId,
                   List<MockApi> mockApis) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.code = code;
        this.enabled = enabled;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createUserId = createUserId;
        this.mockApis = mockApis;
    }

    /**
     * 获取项目ID
     *
     * @return 项目ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置项目ID
     *
     * @param id 项目ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取项目名称
     *
     * @return 项目名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置项目名称
     *
     * @param name 项目名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取项目描述
     *
     * @return 项目描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置项目描述
     *
     * @param description 项目描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取项目编码
     *
     * @return 项目编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置项目编码
     *
     * @param code 项目编码
     */
    public void setCode(String code) {
        this.code = code;
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
     * 获取项目下的API列表
     *
     * @return 项目下的API列表
     */
    @JsonIgnoreProperties({"project", "responses"})
    public List<MockApi> getMockApis() {
        return mockApis;
    }

    /**
     * 设置项目下的API列表
     *
     * @param mockApis 项目下的API列表
     */
    public void setMockApis(List<MockApi> mockApis) {
        this.mockApis = mockApis;
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
        Project project = (Project) o;
        return Objects.equals(id, project.id);
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
        return "Project{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", code='" + code + '\'' +
                ", enabled=" + enabled +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", createUserId=" + createUserId +
                ", mockApis=" + mockApis +
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
}
