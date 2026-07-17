/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据库检查工具 - 用于检查和初始化用户
 *
 * @author carolcoral
 */
@Component
@Order(1) // 在 StartupConfig 之前执行
public class DatabaseChecker implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseChecker.class);

    /**
     * 构造器
     */
    public DatabaseChecker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        log.info("==================== 数据库用户检查 ====================");
        
        try {
            // 检查数据库中是否有用户
            long userCount = userRepository.count();
            log.info("数据库中用户总数: {}", userCount);
            
            if (userCount == 0) {
                log.warn("警告：数据库中没有用户！请先创建管理员账号");
                log.warn("可以使用默认凭证登录：admin / Admin@123!");
            } else {
                // 列出所有用户
                log.info("数据库中的用户列表：");
                userRepository.findAll().forEach(user -> {
                    log.info("  - ID: {}, 用户名: {}, 邮箱: {}, 角色: {}, 状态: {}",
                            user.getId(),
                            user.getUsername(),
                            user.getEmail(),
                            user.getRole(),
                            user.isEnabled() ? "启用" : "禁用");
                });
                
                // 检查是否有 admin 用户
                boolean hasAdmin = userRepository.findByUsername("admin").isPresent();
                if (hasAdmin) {
                    log.info("✓ Admin 用户存在");
                } else {
                    log.warn("✗ Admin 用户不存在！");
                }
            }
            
            log.info("==================== 数据库检查完成 ====================");
        } catch (Exception e) {
            log.error("数据库检查失败: {}", e.getMessage(), e);
        }
    }
}
