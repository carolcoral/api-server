/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * 登录请求DTO
 *
 * @author carolcoral
 */
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "用户名或邮箱", example = "admin", required = true)
    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(min = 3, max = 100, message = "账号长度必须在3-100个字符之间")
    private String username;

    @Schema(description = "密码", example = "Admin@123", required = true)
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度必须在8-100个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "密码必须包含大小写字母、数字和特殊字符")
    private String password;

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
