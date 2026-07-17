/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import com.carolcoral.apiserver.entity.ProjectMember;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 项目成员DTO
 * 用于返回包含用户信息的项目成员数据
 *
 * @author carolcoral
 */
@Schema(description = "项目成员DTO")
public class ProjectMemberDTO {

    @Schema(description = "成员ID")
    private Long id;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户邮箱")
    private String email;

    @Schema(description = "成员角色")
    private ProjectMember.MemberRole role;

    @Schema(description = "加入时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 默认构造器
     */
    public ProjectMemberDTO() {
    }

    /**
     * Builder方法
     */
    public static ProjectMemberDTOBuilder builder() {
        return new ProjectMemberDTOBuilder();
    }

    /**
     * Builder类
     */
    public static class ProjectMemberDTOBuilder {
        private Long id;
        private Long projectId;
        private Long userId;
        private String username;
        private String email;
        private ProjectMember.MemberRole role;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public ProjectMemberDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProjectMemberDTOBuilder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public ProjectMemberDTOBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public ProjectMemberDTOBuilder username(String username) {
            this.username = username;
            return this;
        }

        public ProjectMemberDTOBuilder email(String email) {
            this.email = email;
            return this;
        }

        public ProjectMemberDTOBuilder role(ProjectMember.MemberRole role) {
            this.role = role;
            return this;
        }

        public ProjectMemberDTOBuilder createTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public ProjectMemberDTOBuilder updateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public ProjectMemberDTO build() {
            ProjectMemberDTO dto = new ProjectMemberDTO();
            dto.id = id;
            dto.projectId = projectId;
            dto.userId = userId;
            dto.username = username;
            dto.email = email;
            dto.role = role;
            dto.createTime = createTime;
            dto.updateTime = updateTime;
            return dto;
        }
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public ProjectMember.MemberRole getRole() {
        return role;
    }

    public void setRole(ProjectMember.MemberRole role) {
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
}
