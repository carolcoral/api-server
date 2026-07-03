/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.dto.LoginRequest;
import com.carolcoral.mockserver.dto.LoginResponse;
import com.carolcoral.mockserver.dto.PageResult;
import com.carolcoral.mockserver.entity.Role;
import com.carolcoral.mockserver.entity.User;
import com.carolcoral.mockserver.repository.RoleRepository;
import com.carolcoral.mockserver.repository.UserRepository;
import com.carolcoral.mockserver.util.JwtTokenUtil;
import com.carolcoral.mockserver.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户服务类
 *
 * @author carolcoral
 */
@Tag(name = "用户服务", description = "用户业务逻辑处理")
@Service
public class UserService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    /**
     * 构造器
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, 
                       JwtTokenUtil jwtTokenUtil, EmailService emailService,
                       RoleRepository roleRepository, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenUtil = jwtTokenUtil;
        this.emailService = emailService;
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final EmailService emailService;
    private final RoleRepository roleRepository;
    private final PermissionService permissionService;

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录响应
     */
    @Operation(summary = "用户登录")
    public ApiResponse<LoginResponse> login(@Parameter(description = "登录请求") LoginRequest loginRequest) {
        try {
            String account = loginRequest.getUsername();
            Optional<User> userOpt;

            // 判断输入是邮箱还是用户名（含@则为邮箱）
            if (account.contains("@")) {
                userOpt = userRepository.findByEmail(account);
            } else {
                userOpt = userRepository.findByUsername(account);
            }

            if (!userOpt.isPresent()) {
                return ApiResponse.error(401, "用户名或密码错误");
            }
            
            User user = userOpt.get();
            
            if (!user.getEnabled()) {
                return ApiResponse.error(403, "用户已被禁用");
            }
            
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ApiResponse.error(401, "用户名或密码错误");
            }

            // 生成JWT令牌
            String token = jwtTokenUtil.generateToken(user, user.getId(), user.getRole().name());

            // 获取用户权限列表及角色完整信息
            List<String> permList = new ArrayList<>();
            Long roleId = user.getRoleId();
            String roleName = null;
            String roleCode = null;
            if (roleId != null) {
                Set<String> permCodes = permissionService.getUserPermissionCodes(
                        java.util.Collections.singletonList(roleId));
                permList = new ArrayList<>(permCodes);
                // 查询角色完整信息（名称、编码），供前端展示铭牌使用，避免前端再调受权限控制的 role 接口
                Optional<Role> roleOpt = roleRepository.findById(roleId);
                if (roleOpt.isPresent()) {
                    Role userRole = roleOpt.get();
                    roleName = userRole.getName();
                    roleCode = userRole.getCode();
                }
            }

            LoginResponse loginResponse = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .roleId(roleId)
                    .roleName(roleName)
                    .roleCode(roleCode)
                    .language(user.getLanguage())
                    .permissions(permList)
                    .expiresIn(jwtTokenUtil.getExpiration())
                    .build();

            log.info("用户登录成功: {}", user.getUsername());
            return ApiResponse.success(loginResponse);

        } catch (Exception e) {
            log.error("用户登录失败: {}", e.getMessage(), e);
            return ApiResponse.error("登录失败，请稍后重试");
        }
    }

    /**
     * 更新用户个人信息
     *
     * @param user 用户信息
     * @return 更新的用户
     */
    @Transactional
    public ApiResponse<User> updateUserProfile(User user) {
        try {
            Optional<User> existingUserOpt = userRepository.findById(user.getId());
            
            if (!existingUserOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User existingUser = existingUserOpt.get();
            
            // 只允许更新用户自己的信息
            existingUser.setEmail(user.getEmail());
            existingUser.setLanguage(user.getLanguage());
            
            User updatedUser = userRepository.save(existingUser);
            log.info("用户个人信息更新成功: {}", updatedUser.getUsername());
            return ApiResponse.success(updatedUser);
            
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", e.getMessage());
            return ApiResponse.error("更新用户信息失败，请稍后重试");
        }
    }

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建的用户
     */
    @Operation(summary = "创建用户")
    @Transactional
    public ApiResponse<User> createUser(@Parameter(description = "用户信息") User user) {
        try {
            // 检查用户名是否已存在
            if (userRepository.existsByUsername(user.getUsername())) {
                return ApiResponse.error("用户名已存在");
            }

            // 检查邮箱是否已存在
            if (user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
                return ApiResponse.error("邮箱已存在");
            }

            // 加密密码
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            
            // 设置默认角色
            if (user.getRole() == null) {
                user.setRole(User.UserRole.USER);
            }
            
            // 如果未指定 roleId，根据角色获取默认角色ID
            if (user.getRoleId() == null) {
                resolveRoleId(user);
            }

            User savedUser = userRepository.save(user);
            log.info("创建用户成功: {}", savedUser.getUsername());
            return ApiResponse.success(savedUser);

        } catch (Exception e) {
            log.error("创建用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建用户失败，请稍后重试");
        }
    }

    /**
     * 更新用户（管理员操作）
     *
     * @param user 用户信息
     * @return 更新的用户
     */
    @Operation(summary = "更新用户")
    @Transactional
    public ApiResponse<User> updateUser(@Parameter(description = "用户信息") User user) {
        try {
            Optional<User> existingUserOpt = userRepository.findById(user.getId());
            
            if (!existingUserOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User existingUser = existingUserOpt.get();
            String newPlainPassword = null;
            
            // 更新用户信息
            if (user.getEmail() != null) {
                existingUser.setEmail(user.getEmail());
            }
            if (user.getRole() != null) {
                existingUser.setRole(user.getRole());
            }
            if (user.getRoleId() != null) {
                existingUser.setRoleId(user.getRoleId());
            }
            if (user.getEnabled() != null) {
                existingUser.setEnabled(user.getEnabled());
            }

            // 如果更新了密码，需要加密并记录明文以发送邮件
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                newPlainPassword = user.getPassword();
                existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            User updatedUser = userRepository.save(existingUser);
            log.info("更新用户成功: {}", updatedUser.getUsername());

            // 如果密码被修改且用户有邮箱，尝试发送通知邮件
            if (newPlainPassword != null && updatedUser.getEmail() != null && !updatedUser.getEmail().isEmpty()) {
                try {
                    sendPasswordChangedNotification(updatedUser, newPlainPassword);
                } catch (Exception mailEx) {
                    log.warn("发送密码修改通知邮件失败（不影响更新操作）: {}", mailEx.getMessage());
                }
            }

            return ApiResponse.success(updatedUser);

        } catch (Exception e) {
            log.error("更新用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新用户失败，请稍后重试");
        }
    }

    /**
     * 发送密码修改通知邮件（优先使用 PASSWORD_CHANGED 类型模板，无则用默认内容）
     */
    private void sendPasswordChangedNotification(User user, String newPlainPassword) {
        boolean sent = emailService.sendPasswordChangedEmail(user.getEmail(), user.getUsername(), newPlainPassword);
        if (sent) {
            log.info("密码修改通知邮件已发送: username={}, email={}", user.getUsername(), user.getEmail());
        } else {
            log.warn("密码修改通知邮件发送失败（邮件服务未配置或不可用）: username={}", user.getUsername());
        }
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @Operation(summary = "删除用户")
    @Transactional
    public ApiResponse<Void> deleteUser(@Parameter(description = "用户ID", example = "1") Long userId) {
        try {
            if (!userRepository.existsById(userId)) {
                return ApiResponse.error("用户不存在");
            }

            // 不能删除管理员账号
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent() && userOpt.get().getRole() == User.UserRole.ADMIN) {
                return ApiResponse.error("不能删除管理员账号");
            }

            userRepository.deleteById(userId);
            log.info("删除用户成功: {}", userId);
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("删除用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除用户失败，请稍后重试");
        }
    }

    /**
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @Operation(summary = "根据ID查询用户")
    public ApiResponse<User> getUserById(@Parameter(description = "用户ID", example = "1") Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            return ApiResponse.success(userOpt.get());

        } catch (Exception e) {
            log.error("查询用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户失败，请稍后重试");
        }
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    @Operation(summary = "根据用户名查询用户")
    public ApiResponse<User> getUserByUsername(@Parameter(description = "用户名", example = "admin") String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            return ApiResponse.success(userOpt.get());

        } catch (Exception e) {
            log.error("查询用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户失败，请稍后重试");
        }
    }

    /**
     * 搜索用户（支持用户名或邮箱模糊搜索，所有已认证用户可用）
     *
     * @param keyword 搜索关键词
     * @return 用户列表
     */
    @Operation(summary = "搜索用户")
    public ApiResponse<List<User>> searchUsers(String keyword) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return ApiResponse.success(java.util.Collections.emptyList());
            }
            String pattern = "%" + keyword.trim() + "%";
            List<User> users = userRepository.findByUsernameLikeOrEmailLike(pattern, pattern);
            return ApiResponse.success(users);
        } catch (Exception e) {
            log.error("搜索用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("搜索用户失败，请稍后重试");
        }
    }

    /**
     * 检查邮箱是否已被使用
     *
     * @param email 邮箱
     * @return 是否已存在
     */
    public boolean isEmailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 检查用户名是否已被使用
     *
     * @param username 用户名
     * @return 是否已存在
     */
    public boolean isUsernameTaken(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    @Operation(summary = "查询所有用户")
    public ApiResponse<List<User>> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            return ApiResponse.success(users);

        } catch (Exception e) {
            log.error("查询用户列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户列表失败，请稍后重试");
        }
    }

    /**
     * 分页搜索用户（支持多条件过滤）
     */
    @Operation(summary = "分页搜索用户")
    public ApiResponse<PageResult<User>> searchUsers(String username, String email, Long roleId,
            User.UserRole role, Boolean enabled, int page, int size) {
        try {
            Specification<User> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (username != null && !username.isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
                }
                if (email != null && !email.isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
                }
                if (roleId != null) {
                    predicates.add(cb.equal(root.get("roleId"), roleId));
                }
                if (role != null) {
                    predicates.add(cb.equal(root.get("role"), role));
                }
                if (enabled != null) {
                    predicates.add(cb.equal(root.get("enabled"), enabled));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<User> result = userRepository.findAll(spec, pageRequest);
            PageResult<User> pageResult = new PageResult<>();
            pageResult.setContent(result.getContent());
            pageResult.setPage(result.getNumber());
            pageResult.setSize(result.getSize());
            pageResult.setTotalElements(result.getTotalElements());
            pageResult.setTotalPages(result.getTotalPages());
            pageResult.setFirst(result.isFirst());
            pageResult.setLast(result.isLast());
            return ApiResponse.success(pageResult);
        } catch (Exception e) {
            log.error("分页搜索用户失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户列表失败，请稍后重试");
        }
    }

    /**
     * 根据角色查询用户
     *
     * @param role 用户角色
     * @return 用户列表
     */
    @Operation(summary = "根据角色查询用户")
    public ApiResponse<List<User>> getUsersByRole(@Parameter(description = "用户角色") User.UserRole role) {
        try {
            List<User> users = userRepository.findByRole(role);
            return ApiResponse.success(users);

        } catch (Exception e) {
            log.error("查询用户列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户列表失败，请稍后重试");
        }
    }

    /**
     * 查询启用状态的用户
     *
     * @return 用户列表
     */
    @Operation(summary = "查询启用状态的用户")
    public ApiResponse<List<User>> getEnabledUsers() {
        try {
            List<User> users = userRepository.findByEnabledTrue();
            return ApiResponse.success(users);

        } catch (Exception e) {
            log.error("查询用户列表失败: {}", e.getMessage());
            return ApiResponse.error("查询用户列表失败，请稍后重试");
        }
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    @Operation(summary = "获取当前登录用户信息")
    public ApiResponse<User> getCurrentUser() {
        try {
            org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return ApiResponse.error("用户未登录");
            }

            User currentUser = (User) authentication.getPrincipal();
            return ApiResponse.success(currentUser);
        } catch (Exception e) {
            log.error("获取当前用户失败: {}", e.getMessage());
            return ApiResponse.error("获取用户信息失败");
        }
    }

    /**
     * 修改密码
     *
     * @param passwordChangeRequest 密码修改请求
     * @return 操作结果
     */
    @Operation(summary = "修改密码")
    @Transactional
    public ApiResponse<Void> changePassword(PasswordChangeRequest passwordChangeRequest) {
        try {
            org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return ApiResponse.error("用户未登录");
            }

            User currentUser = (User) authentication.getPrincipal();

            // 验证原密码
            if (!passwordEncoder.matches(passwordChangeRequest.getOldPassword(), currentUser.getPassword())) {
                return ApiResponse.error("原密码错误");
            }

            // 验证新密码和确认密码是否一致
            if (!passwordChangeRequest.getNewPassword().equals(passwordChangeRequest.getConfirmPassword())) {
                return ApiResponse.error("新密码和确认密码不一致");
            }

            // 更新密码
            currentUser.setPassword(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));
            userRepository.save(currentUser);

            log.info("用户 {} 修改密码成功", currentUser.getUsername());

            // 发送安全通知邮件（异步发送，不影响主流程）
            try {
                if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                    emailService.sendPasswordChangedBySelfEmail(
                            currentUser.getEmail(), currentUser.getUsername());
                }
            } catch (Exception e) {
                log.warn("发送密码修改通知邮件失败（不影响密码修改）: {}", e.getMessage());
            }

            return ApiResponse.success();
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage());
            return ApiResponse.error("修改密码失败");
        }
    }

    /**
     * 密码修改请求DTO
     */
    public static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }

    /**
     * 通过邮箱重置密码（忘记密码流程）
     *
     * @param email       邮箱
     * @param newPassword 新密码
     * @return 操作结果
     */
    @Transactional
    public ApiResponse<Void> resetPasswordByEmail(String email, String newPassword) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                return ApiResponse.error("用户不存在");
            }

            User user = userOpt.get();
            if (!user.getEnabled()) {
                return ApiResponse.error("用户已被禁用");
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            log.info("用户 {} 通过邮箱重置密码成功", user.getUsername());
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("重置密码失败: {}", e.getMessage());
            return ApiResponse.error("重置密码失败");
        }
    }

    /**
     * 获取所有可用角色（供前端下拉选择）
     */
    public ApiResponse<List<Role>> getAvailableRoles() {
        try {
            List<Role> roles = roleRepository.findAll();
            return ApiResponse.success(roles);
        } catch (Exception e) {
            log.error("获取角色列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取角色列表失败");
        }
    }

    /**
     * 根据用户的 UserRole 枚举解析对应的 roleId
     */
    private void resolveRoleId(User user) {
        User.UserRole userRole = user.getRole();
        if (userRole == User.UserRole.ADMIN) {
            roleRepository.findByCode("ROLE_ADMIN").ifPresent(r -> user.setRoleId(r.getId()));
        } else {
            // 普通用户：使用默认角色，如果没有默认角色则回退到 ROLE_USER
            Optional<Role> defaultRole = roleRepository.findByIsDefaultTrue();
            if (defaultRole.isPresent()) {
                user.setRoleId(defaultRole.get().getId());
            } else {
                roleRepository.findByCode("ROLE_USER").ifPresent(r -> user.setRoleId(r.getId()));
            }
        }
    }
}
