/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import com.carolcoral.apiserver.entity.Project;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 项目及用户角色DTO
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "项目及用户角色")
public class ProjectWithRoleDTO {

    @Schema(description = "项目ID", example = "1")
    private Long id;

    @Schema(description = "项目名称", example = "电商平台API")
    private String name;

    @Schema(description = "项目描述", example = "电商平台的API接口模拟")
    private String description;

    @Schema(description = "项目编码（唯一）", example = "ecmall")
    private String code;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "创建人ID", example = "1")
    private Long createUserId;

    @Schema(description = "当前用户在该项目中的角色", example = "ADMIN")
    private String userRole;

    /**
     * 默认构造器
     */
    public ProjectWithRoleDTO() {
    }

    /**
     * Builder方法
     */
    public static ProjectWithRoleDTOBuilder builder() {
        return new ProjectWithRoleDTOBuilder();
    }

    /**
     * Builder类
     */
    public static class ProjectWithRoleDTOBuilder {
        private Long id;
        private String name;
        private String description;
        private String code;
        private Boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long createUserId;
        private String userRole;

        public ProjectWithRoleDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ProjectWithRoleDTOBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ProjectWithRoleDTOBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ProjectWithRoleDTOBuilder code(String code) {
            this.code = code;
            return this;
        }

        public ProjectWithRoleDTOBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ProjectWithRoleDTOBuilder createTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public ProjectWithRoleDTOBuilder updateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public ProjectWithRoleDTOBuilder createUserId(Long createUserId) {
            this.createUserId = createUserId;
            return this;
        }

        public ProjectWithRoleDTOBuilder userRole(String userRole) {
            this.userRole = userRole;
            return this;
        }

        public ProjectWithRoleDTO build() {
            ProjectWithRoleDTO dto = new ProjectWithRoleDTO();
            dto.id = id;
            dto.name = name;
            dto.description = description;
            dto.code = code;
            dto.enabled = enabled;
            dto.createTime = createTime;
            dto.updateTime = updateTime;
            dto.createUserId = createUserId;
            dto.userRole = userRole;
            return dto;
        }
    }

    /**
     * 从Project实体和角色转换为DTO
     *
     * @param project 项目实体
     * @param role    用户角色
     * @return DTO
     */
    public static ProjectWithRoleDTO fromProject(Project project, String role) {
        return ProjectWithRoleDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .code(project.getCode())
                .enabled(project.getEnabled())
                .createTime(project.getCreateTime())
                .updateTime(project.getUpdateTime())
                .createUserId(project.getCreateUserId())
                .userRole(role)
                .build();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
}
