/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 项目成员实体类
 * 用于管理项目成员及其角色（创建者/管理员/成员）
 *
 * @author carolcoral
 * @version 1.1
 * @since 2026-03-05
 */
@Schema(description = "项目成员实体")
@Entity
@Table(name = "t_project_member")
public class ProjectMember {

    @Schema(description = "成员ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "项目ID", example = "1")
    @NotNull
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Schema(description = "用户ID", example = "1")
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 成员角色: CREATOR(创建者), ADMIN(管理员), MEMBER(普通成员)
     */
    @Schema(description = "成员角色", example = "MEMBER", allowableValues = {"CREATOR", "ADMIN", "MEMBER"})
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /**
     * 关联的项目对象（用于查询）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"members", "mockApis"})
    private Project project;

    /**
     * 关联的用户对象（用于查询）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"password", "authorities"})
    private User user;

    /**
     * 成员角色枚举
     */
    @Schema(description = "成员角色枚举")
    public enum MemberRole {
        /**
         * 创建者 - 自动拥有管理员权限
         */
        CREATOR,

        /**
         * 管理员 - 可以管理项目和项目内接口
         */
        ADMIN,

        /**
         * 普通成员 - 只能访问项目
         */
        MEMBER
    }

    /**
     * 无参构造函数
     */
    public ProjectMember() {
    }

    /**
     * 全参构造函数
     *
     * @param id 成员ID
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param role 成员角色
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    public ProjectMember(Long id, Long projectId, Long userId, MemberRole role,
                      LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "members", "mockApis"})
    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "authorities"})
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMember that = (ProjectMember) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProjectMember{" +
                "id=" + id +
                ", projectId=" + projectId +
                ", userId=" + userId +
                ", role=" + role +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
