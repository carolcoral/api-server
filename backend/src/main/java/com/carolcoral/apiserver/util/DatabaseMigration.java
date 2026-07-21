/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DatabaseMigration implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseDialectProvider dialect;

    @Override
    public void run(String... args) {
        log.info("当前数据库类型: {}, URL: {}", dialect.detectDbType(), dialect.isSqlite() ? "SQLite" : "MySQL/PostgreSQL");

        // SQLite 特有的 ALTER TABLE 迁移（MySQL/PostgreSQL 由 Hibernate ddl-auto 处理）
        if (dialect.isSqlite()) {
            runSqliteMigrations();
        }

        // 通用数据迁移（所有数据库都执行）
        runCommonMigrations();
    }

    /**
     * SQLite 特有的表结构迁移（MySQL/PostgreSQL 由 Hibernate ddl-auto:update 处理）
     */
    private void runSqliteMigrations() {
        // 添加active字段到t_mock_response表
        safeAlter("ALTER TABLE t_mock_response ADD COLUMN active BOOLEAN DEFAULT 0", "active");

        // 添加is_default字段到t_mock_response表
        safeAlter("ALTER TABLE t_mock_response ADD COLUMN is_default BOOLEAN DEFAULT 0", "is_default");

        // 添加response_delay字段到t_mock_response表
        safeAlter("ALTER TABLE t_mock_response ADD COLUMN response_delay INTEGER DEFAULT 0", "response_delay");

        // 创建t_response_request_param表
        safeExecute("CREATE TABLE IF NOT EXISTS t_response_request_param (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "param_name VARCHAR(100) NOT NULL," +
            "param_type VARCHAR(50) NOT NULL," +
            "param_value TEXT," +
            "required BOOLEAN NOT NULL DEFAULT 1," +
            "create_time DATETIME NOT NULL," +
            "update_time DATETIME NOT NULL," +
            "response_id INTEGER NOT NULL," +
            "FOREIGN KEY (response_id) REFERENCES t_mock_response(id))",
            "t_response_request_param");

        // 添加language字段到t_user表
        safeAlter("ALTER TABLE t_user ADD COLUMN language varchar(10)", "language");

        // 添加custom_response_handler字段
        safeAlter("ALTER TABLE t_mock_api ADD COLUMN custom_response_handler VARCHAR(500)", "custom_response_handler");

        // 添加custom_response_source字段
        safeAlter("ALTER TABLE t_mock_api ADD COLUMN custom_response_source TEXT", "custom_response_source");

        // 添加is_system字段
        safeAlter("ALTER TABLE t_custom_code_template ADD COLUMN is_system BOOLEAN DEFAULT 0", "is_system");

        // 代码模板表结构升级（project_id改为可空）
        safeExecute("""
            CREATE TABLE t_custom_code_template_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(500),
                source_code TEXT NOT NULL,
                language VARCHAR(50) NOT NULL DEFAULT 'JAVA',
                enabled BOOLEAN NOT NULL DEFAULT 1,
                is_system BOOLEAN DEFAULT 0,
                create_time DATETIME NOT NULL,
                update_time DATETIME NOT NULL,
                create_user_id BIGINT NOT NULL,
                project_id BIGINT
            )
            """, "t_custom_code_template_new (v2.1.2)");
        safeExecute("""
            INSERT INTO t_custom_code_template_new
            SELECT id, name, description, source_code, language, enabled,
                   COALESCE(is_system, 0), create_time, update_time, create_user_id, project_id
            FROM t_custom_code_template
            """, "代码模板数据迁移");
        safeExecute("DROP TABLE t_custom_code_template", "DROP旧模板表");
        safeExecute("ALTER TABLE t_custom_code_template_new RENAME TO t_custom_code_template", "RENAME新模板表");

        // AI 服务相关表（SQLite 表结构迁移）
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_provider (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name VARCHAR(100) NOT NULL," +
            "code VARCHAR(50) NOT NULL UNIQUE," +
            "base_url VARCHAR(500) NOT NULL," +
            "api_type VARCHAR(30) NOT NULL," +
            "auth_type VARCHAR(20) NOT NULL," +
            "api_key VARCHAR(500)," +
            "status BOOLEAN NOT NULL DEFAULT 1," +
            "description TEXT," +
            "create_time DATETIME NOT NULL," +
            "update_time DATETIME NOT NULL)", "t_ai_provider");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_model (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "provider_id BIGINT NOT NULL," +
            "model_name VARCHAR(100) NOT NULL," +
            "display_name VARCHAR(100)," +
            "input_price REAL," +
            "output_price REAL," +
            "max_tokens INTEGER," +
            "supports_stream BOOLEAN NOT NULL DEFAULT 1," +
            "health_status VARCHAR(20) DEFAULT 'online'," +
            "last_health_check DATETIME," +
            "cooldown_until DATETIME," +
            "consecutive_failures INTEGER NOT NULL DEFAULT 0," +
            "avg_latency_ms BIGINT," +
            "auto_mode BOOLEAN NOT NULL DEFAULT 0," +
            "status BOOLEAN NOT NULL DEFAULT 1," +
            "create_time DATETIME NOT NULL," +
            "update_time DATETIME NOT NULL," +
            "FOREIGN KEY (provider_id) REFERENCES t_ai_provider(id))", "t_ai_model");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_subscription (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_id BIGINT NOT NULL," +
            "provider_id BIGINT NOT NULL," +
            "model_id BIGINT NOT NULL," +
            "priority INTEGER NOT NULL DEFAULT 0," +
            "weight INTEGER NOT NULL DEFAULT 1," +
            "tags VARCHAR(200)," +
            "fallback_enabled BOOLEAN NOT NULL DEFAULT 1," +
            "max_tokens_per_request INTEGER," +
            "status BOOLEAN NOT NULL DEFAULT 1," +
            "expire_time DATETIME," +
            "create_time DATETIME NOT NULL," +
            "update_time DATETIME NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id)," +
            "FOREIGN KEY (provider_id) REFERENCES t_ai_provider(id)," +
            "FOREIGN KEY (model_id) REFERENCES t_ai_model(id))", "t_ai_subscription");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_quota (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_id BIGINT NOT NULL," +
            "subscription_id BIGINT," +
            "token_limit BIGINT NOT NULL," +
            "token_used BIGINT NOT NULL DEFAULT 0," +
            "time_window_seconds INTEGER NOT NULL DEFAULT 18000," +
            "window_start DATETIME," +
            "status BOOLEAN NOT NULL DEFAULT 1," +
            "create_time DATETIME NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id)," +
            "FOREIGN KEY (subscription_id) REFERENCES t_ai_subscription(id))", "t_ai_quota");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_usage_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_id BIGINT NOT NULL," +
            "provider_id BIGINT," +
            "model_id BIGINT," +
            "request_body TEXT," +
            "response_body TEXT," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "cost REAL," +
            "latency_ms BIGINT," +
            "fallback_from BIGINT," +
            "status_code INTEGER," +
            "error_msg VARCHAR(500)," +
            "create_time DATETIME NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id))", "t_ai_usage_log");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_model_health (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "model_id BIGINT NOT NULL," +
            "check_time DATETIME NOT NULL," +
            "status VARCHAR(20) NOT NULL," +
            "latency_ms BIGINT," +
            "error_msg VARCHAR(500)," +
            "FOREIGN KEY (model_id) REFERENCES t_ai_model(id))", "t_ai_model_health");

        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_api_key (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_id BIGINT NOT NULL," +
            "api_key VARCHAR(64) NOT NULL UNIQUE," +
            "key_name VARCHAR(100)," +
            "last_used DATETIME," +
            "status BOOLEAN NOT NULL DEFAULT 1," +
            "create_time DATETIME NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id))", "t_ai_api_key");

        // 添加role_id字段到t_user表
        safeAlter("ALTER TABLE t_user ADD COLUMN role_id BIGINT", "role_id");

        // 添加is_default字段到t_ai_config表
        safeAlter("ALTER TABLE t_ai_config ADD COLUMN is_default BOOLEAN DEFAULT 0", "is_default");

        // 添加models字段到t_ai_config表
        safeAlter("ALTER TABLE t_ai_config ADD COLUMN models VARCHAR(1000)", "models");

        // 添加timeout字段到t_ai_config表
        safeAlter("ALTER TABLE t_ai_config ADD COLUMN timeout INTEGER DEFAULT 120", "timeout");

        // t_ai_model 新增 auto_mode 字段
        safeAlter("ALTER TABLE t_ai_model ADD COLUMN auto_mode BOOLEAN DEFAULT 0", "auto_mode");

        // 请求录制与回放 - t_request_log 新增字段
        safeAlter("ALTER TABLE t_request_log ADD COLUMN request_headers TEXT", "request_headers");
        safeAlter("ALTER TABLE t_request_log ADD COLUMN query_params TEXT", "query_params");
        safeAlter("ALTER TABLE t_request_log ADD COLUMN request_body TEXT", "request_body");
        safeAlter("ALTER TABLE t_request_log ADD COLUMN response_body TEXT", "response_body");
        safeAlter("ALTER TABLE t_request_log ADD COLUMN response_content_type VARCHAR(100)", "response_content_type");
        safeAlter("ALTER TABLE t_request_log ADD COLUMN source VARCHAR(50) DEFAULT 'MOCK'", "source");
    }

    /**
     * 通用数据迁移（所有数据库类型都执行）
     */
    private void runCommonMigrations() {
        // 创建t_system_config表
        safeExecute("CREATE TABLE IF NOT EXISTS t_system_config (" +
            dialect.idColumnDefinition() + "," +
            "config_key VARCHAR(100) NOT NULL UNIQUE," +
            "config_value VARCHAR(500) NOT NULL," +
            "description VARCHAR(500)," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL)", "t_system_config");
        safeInsertOrIgnore("t_system_config",
            "(config_key, config_value, description, create_time, update_time)",
            "config_key",
            "VALUES ('defaultLanguage', 'zh-CN', '系统默认语言', " + dialect.nowExpression() + ", " + dialect.nowExpression() + ")");

        // 创建t_ai_config表
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_config (" +
            dialect.idColumnDefinition() + "," +
            "provider VARCHAR(50) NOT NULL UNIQUE," +
            "provider_name VARCHAR(100) NOT NULL," +
            "api_url VARCHAR(500) NOT NULL," +
            "api_key VARCHAR(500) NOT NULL," +
            "default_model VARCHAR(100)," +
            "max_tokens INTEGER DEFAULT 4096," +
            "temperature REAL DEFAULT 0.7," +
            "timeout INTEGER DEFAULT 120," +
            "enabled BOOLEAN NOT NULL DEFAULT " + dialect.booleanLiteral(false) + "," +
            "is_default BOOLEAN NOT NULL DEFAULT " + dialect.booleanLiteral(false) + "," +
            "models VARCHAR(1000)," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL)", "t_ai_config");

        // 创建t_role表
        safeExecute("CREATE TABLE IF NOT EXISTS t_role (" +
            dialect.idColumnDefinition() + "," +
            "name VARCHAR(50) NOT NULL UNIQUE," +
            "code VARCHAR(50) NOT NULL UNIQUE," +
            "description VARCHAR(200)," +
            "is_default BOOLEAN NOT NULL DEFAULT " + dialect.booleanLiteral(false) + "," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL)", "t_role");
        String now = dialect.nowExpression();
        safeInsertOrIgnore("t_role", "(id, name, code, description, is_default, create_time, update_time)", "id",
            "VALUES (1, '管理员', 'ROLE_ADMIN', '系统管理员，拥有所有权限', " + dialect.booleanLiteral(false) + ", " + now + ", " + now + ")");
        safeInsertOrIgnore("t_role", "(id, name, code, description, is_default, create_time, update_time)", "id",
            "VALUES (2, '普通用户', 'ROLE_USER', '默认注册用户角色', " + dialect.booleanLiteral(true) + ", " + now + ", " + now + ")");

        // PostgreSQL：同步 BIGSERIAL 序列，确保后续 INSERT 不与硬编码 ID 冲突
        if (dialect.usesSequenceForId()) {
            try {
                jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('t_role', 'id'), COALESCE(MAX(id), 1)) FROM t_role");
                log.info("已同步 t_role 序列值");
            } catch (Exception e) {
                log.warn("同步 t_role 序列失败（非致命）: {}", e.getMessage());
            }
        }

        // 创建t_permission表
        safeExecute("CREATE TABLE IF NOT EXISTS t_permission (" +
            dialect.idColumnDefinition() + "," +
            "name VARCHAR(100) NOT NULL," +
            "code VARCHAR(100) NOT NULL UNIQUE," +
            "group_name VARCHAR(50) NOT NULL," +
            "type VARCHAR(20) NOT NULL," +
            "sort_order INTEGER NOT NULL DEFAULT 0," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL)", "t_permission");
        insertDefaultPermissions();

        // 创建t_role_permission表
        safeExecute("CREATE TABLE IF NOT EXISTS t_role_permission (" +
            dialect.idColumnDefinition() + "," +
            "role_id BIGINT NOT NULL," +
            "permission_id BIGINT NOT NULL)", "t_role_permission");
        // PostgreSQL/MySQL: 确保 role_id + permission_id 有唯一约束（支持 ON CONFLICT）
        safeExecute("CREATE UNIQUE INDEX IF NOT EXISTS uk_role_permission ON t_role_permission(role_id, permission_id)",
            "uk_role_permission");
        // 给管理员角色分配所有权限
        String assignPerms = dialect.buildInsertOrIgnoreFull("t_role_permission", "(role_id, permission_id)",
            "role_id,permission_id", "SELECT 1, id FROM t_permission");
        safeExecute(assignPerms, "管理员权限分配");

        // 更新用户的role_id
        try {
            jdbcTemplate.update("UPDATE t_user SET role_id = 1 WHERE role = 'ADMIN' AND role_id IS NULL");
            jdbcTemplate.update("UPDATE t_user SET role_id = 2 WHERE role = 'USER' AND role_id IS NULL");
            log.info("已更新现有用户的role_id");
        } catch (Exception e) {
            log.warn("更新现有用户role_id失败: {}", e.getMessage());
        }

        // 创建t_ai_call_log表
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_call_log (" +
            dialect.idColumnDefinition() + "," +
            "user_id BIGINT NOT NULL," +
            "username VARCHAR(100)," +
            "api_type VARCHAR(50) NOT NULL," +
            "call_time " + dialect.dateTimeType() + " NOT NULL," +
            "success BOOLEAN," +
            "error_message VARCHAR(500))", "t_ai_call_log");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_call_time", "t_ai_call_log", "call_time"), "idx_ai_call_time");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_call_username", "t_ai_call_log", "username"), "idx_ai_call_username");

        // ========== AI 接入服务表 ==========

        // t_ai_provider - AI 服务商
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_provider (" +
            dialect.idColumnDefinition() + "," +
            "name VARCHAR(100) NOT NULL," +
            "code VARCHAR(50) NOT NULL UNIQUE," +
            "base_url VARCHAR(500) NOT NULL," +
            "api_type VARCHAR(30) NOT NULL," +
            "auth_type VARCHAR(20) NOT NULL," +
            "api_key VARCHAR(500)," +
            "status " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "description TEXT," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL)", "t_ai_provider");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_provider_code", "t_ai_provider", "code"), "idx_ai_provider_code");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_provider_status", "t_ai_provider", "status"), "idx_ai_provider_status");

        // t_ai_model - AI 模型
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_model (" +
            dialect.idColumnDefinition() + "," +
            "provider_id BIGINT NOT NULL," +
            "model_name VARCHAR(100) NOT NULL," +
            "display_name VARCHAR(100)," +
            "input_price DOUBLE PRECISION," +
            "output_price DOUBLE PRECISION," +
            "max_tokens INTEGER," +
            "supports_stream " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "health_status VARCHAR(20) DEFAULT 'online'," +
            "last_health_check " + dialect.dateTimeType() + "," +
            "cooldown_until " + dialect.dateTimeType() + "," +
            "consecutive_failures INTEGER NOT NULL DEFAULT 0," +
            "avg_latency_ms BIGINT," +
            "auto_mode " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(false) + "," +
            "status " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL," +
            "FOREIGN KEY (provider_id) REFERENCES t_ai_provider(id))", "t_ai_model");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_model_provider", "t_ai_model", "provider_id"), "idx_ai_model_provider");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_model_health", "t_ai_model", "health_status"), "idx_ai_model_health");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_model_status", "t_ai_model", "status"), "idx_ai_model_status");

        // t_ai_subscription - 用户订阅
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_subscription (" +
            dialect.idColumnDefinition() + "," +
            "user_id BIGINT NOT NULL," +
            "provider_id BIGINT NOT NULL," +
            "model_id BIGINT NOT NULL," +
            "priority INTEGER NOT NULL DEFAULT 0," +
            "weight INTEGER NOT NULL DEFAULT 1," +
            "tags VARCHAR(200)," +
            "fallback_enabled " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "max_tokens_per_request INTEGER," +
            "status " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "expire_time " + dialect.dateTimeType() + "," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "update_time " + dialect.dateTimeType() + " NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id)," +
            "FOREIGN KEY (provider_id) REFERENCES t_ai_provider(id)," +
            "FOREIGN KEY (model_id) REFERENCES t_ai_model(id))", "t_ai_subscription");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_sub_user", "t_ai_subscription", "user_id"), "idx_ai_sub_user");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_sub_model", "t_ai_subscription", "model_id"), "idx_ai_sub_model");

        // t_ai_quota - 额度管理
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_quota (" +
            dialect.idColumnDefinition() + "," +
            "user_id BIGINT NOT NULL," +
            "subscription_id BIGINT," +
            "token_limit BIGINT NOT NULL," +
            "token_used BIGINT NOT NULL DEFAULT 0," +
            "time_window_seconds INTEGER NOT NULL DEFAULT 18000," +
            "window_start " + dialect.dateTimeType() + "," +
            "status " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id)," +
            "FOREIGN KEY (subscription_id) REFERENCES t_ai_subscription(id))", "t_ai_quota");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_quota_user", "t_ai_quota", "user_id"), "idx_ai_quota_user");

        // t_ai_usage_log - 调用日志
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_usage_log (" +
            dialect.idColumnDefinition() + "," +
            "user_id BIGINT NOT NULL," +
            "provider_id BIGINT," +
            "model_id BIGINT," +
            "request_body TEXT," +
            "response_body TEXT," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "cost DOUBLE PRECISION," +
            "latency_ms BIGINT," +
            "fallback_from BIGINT," +
            "status_code INTEGER," +
            "error_msg VARCHAR(500)," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id))", "t_ai_usage_log");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_usage_user", "t_ai_usage_log", "user_id"), "idx_ai_usage_user");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_usage_time", "t_ai_usage_log", "create_time"), "idx_ai_usage_time");

        // t_ai_model_health - 健康检查记录
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_model_health (" +
            dialect.idColumnDefinition() + "," +
            "model_id BIGINT NOT NULL," +
            "check_time " + dialect.dateTimeType() + " NOT NULL," +
            "status VARCHAR(20) NOT NULL," +
            "latency_ms BIGINT," +
            "error_msg VARCHAR(500)," +
            "FOREIGN KEY (model_id) REFERENCES t_ai_model(id))", "t_ai_model_health");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_health_model", "t_ai_model_health", "model_id"), "idx_ai_model_health");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_health_time", "t_ai_model_health", "check_time"), "idx_ai_health_time");

        // t_ai_api_key - API Key
        safeExecute("CREATE TABLE IF NOT EXISTS t_ai_api_key (" +
            dialect.idColumnDefinition() + "," +
            "user_id BIGINT NOT NULL," +
            "api_key VARCHAR(64) NOT NULL UNIQUE," +
            "key_name VARCHAR(100)," +
            "last_used " + dialect.dateTimeType() + "," +
            "status " + dialect.booleanType() + " NOT NULL DEFAULT " + dialect.booleanLiteral(true) + "," +
            "create_time " + dialect.dateTimeType() + " NOT NULL," +
            "FOREIGN KEY (user_id) REFERENCES t_user(id))", "t_ai_api_key");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_apikey_key", "t_ai_api_key", "api_key"), "idx_ai_apikey_key");
        safeExecute(dialect.createIndexIfNotExists("idx_ai_apikey_user", "t_ai_api_key", "user_id"), "idx_ai_apikey_user");

        // ========== AI 接入服务表 END ==========

        // 补全项目创建者的成员记录
        // 先确保有唯一索引（PostgreSQL ON CONFLICT 需要）
        safeExecute("CREATE UNIQUE INDEX IF NOT EXISTS uk_project_member ON t_project_member(project_id, user_id)",
            "uk_project_member");
        try {
            String insertMembers = dialect.buildInsertOrIgnoreFull("t_project_member",
                "(project_id, user_id, role, create_time, update_time)", "project_id,user_id",
                "SELECT p.id, p.create_user_id, 1, " + now + ", " + now
                + " FROM t_project p"
                + " WHERE p.create_user_id IS NOT NULL"
                + " AND NOT EXISTS ("
                + " SELECT 1 FROM t_project_member pm"
                + " WHERE pm.project_id = p.id AND pm.user_id = p.create_user_id"
                + ")");
            int migratedCount = jdbcTemplate.update(insertMembers);
            log.info("补全项目创建者成员记录: 迁移 {} 条", migratedCount);

            int updatedCount = jdbcTemplate.update(
                "UPDATE t_project_member SET role = 1, update_time = " + now + " WHERE role = 0");
            if (updatedCount > 0) {
                log.info("将 CREATOR(0) 角色统一更新为 ADMIN(1): {} 条", updatedCount);
            }
        } catch (Exception e) {
            log.warn("补全项目创建者成员记录失败: {}", e.getMessage());
        }

        // 更新现有用户语言
        try {
            jdbcTemplate.update("UPDATE t_user SET language = 'zh-CN' WHERE language IS NULL");
            log.info("为现有用户设置默认语言");
        } catch (Exception e) {
            log.warn("设置默认语言失败: {}", e.getMessage());
        }
    }

    /**
     * 安全的 ALTER TABLE（仅 SQLite 执行）
     */
    private void safeAlter(String sql, String columnName) {
        if (!dialect.isSqlite()) return;
        try {
            jdbcTemplate.execute(sql);
            log.info("成功添加{}字段", columnName);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("duplicate column name")) {
                log.info("{}字段已存在，跳过迁移", columnName);
            } else {
                log.warn("添加{}字段失败: {}", errorMsg, columnName);
            }
        }
    }

    /**
     * 安全的执行 SQL（自动选择 execute/update，忽略表已存在的错误）
     */
    private void safeExecute(String sql, String name) {
        String trimmed = sql.trim().toUpperCase();
        try {
            // DML 语句（INSERT/UPDATE/DELETE/SELECT）使用 Statement.execute，DDL 使用 execute
            if (trimmed.startsWith("INSERT") || trimmed.startsWith("UPDATE")
                || trimmed.startsWith("DELETE") || trimmed.startsWith("SELECT")) {
                jdbcTemplate.execute((java.sql.Statement stmt) -> {
                    stmt.execute(sql);
                    return null;
                });
            } else {
                jdbcTemplate.execute(sql);
            }
            log.info("成功执行: {}", name);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("already exists") || errorMsg.contains("no such table"))) {
                log.info("{}已存在或无需迁移，跳过", name);
            } else if (trimmed.startsWith("CREATE TABLE") || trimmed.startsWith("ALTER TABLE")) {
                // DDL 建表失败通常是严重问题（如 PGSQL 类型不兼容），需要 ERROR 级别
                log.error("执行{}失败（可能影响功能）: {}", name, errorMsg);
            } else {
                log.warn("执行{}失败: {}", name, errorMsg);
            }
        }
    }

    /**
     * 安全的 INSERT OR IGNORE（自动处理数据库方言差异）
     */
    private void safeInsertOrIgnore(String table, String columns, String conflictColumn, String values) {
        try {
            String sql = dialect.buildInsertOrIgnoreFull(table, columns, conflictColumn, values);
            jdbcTemplate.execute((java.sql.Statement stmt) -> {
                stmt.execute(sql);
                return null;
            });
        } catch (Exception e) {
            log.error("插入默认数据到{}失败（可能影响功能）: {}", table, e.getMessage());
        }
    }

    /**
     * 插入默认权限定义
     */
    private void insertDefaultPermissions() {
        String now = dialect.nowExpression();
        String[][] perms = {
            // 仪表盘
            {"仪表盘-页面访问", "dashboard:view", "仪表盘", "PAGE", "1"},
            // 业务管理
            {"项目管理-页面访问", "project:view", "业务管理", "PAGE", "10"},
            {"项目管理-创建", "project:create", "业务管理", "BUTTON", "11"},
            {"项目管理-编辑", "project:edit", "业务管理", "BUTTON", "12"},
            {"项目管理-删除", "project:delete", "业务管理", "BUTTON", "13"},
            {"项目管理-查看全部", "project:view_all", "业务管理", "BUTTON", "14"},
            {"项目管理-导入Swagger", "project:import_swagger", "业务管理", "BUTTON", "15"},
            {"项目管理-导出Swagger", "project:export_swagger", "业务管理", "BUTTON", "16"},
            {"项目管理-导出项目数据", "project:export_data", "业务管理", "BUTTON", "17"},
            {"项目管理-导入项目数据", "project:import_data", "业务管理", "BUTTON", "18"},
            {"接口管理-页面访问", "api:view", "业务管理", "PAGE", "20"},
            {"接口管理-创建", "api:create", "业务管理", "BUTTON", "21"},
            {"接口管理-编辑", "api:edit", "业务管理", "BUTTON", "22"},
            {"接口管理-删除", "api:delete", "业务管理", "BUTTON", "23"},
            {"接口管理-查看全部", "api:view_all", "业务管理", "BUTTON", "24"},
            {"接口管理-模板引擎", "api:template_engine", "业务管理", "BUTTON", "25"},
            {"代码模板-页面访问", "code-template:view", "业务管理", "PAGE", "30"},
            {"代码模板-创建", "code-template:create", "业务管理", "BUTTON", "31"},
            {"代码模板-编辑", "code-template:edit", "业务管理", "BUTTON", "32"},
            {"代码模板-删除", "code-template:delete", "业务管理", "BUTTON", "33"},
            {"代码模板-查看全部", "code-template:view_all", "业务管理", "BUTTON", "34"},
            // AI 对话
            {"AI对话-页面访问", "ai-chat:view", "AI对话", "PAGE", "40"},
            // 数据统计
            {"数据统计-页面访问", "statistics:view", "数据统计", "PAGE", "50"},
            {"调试面板-页面访问", "debug-panel:view", "数据统计", "PAGE", "51"},
            // 权限管理
            {"权限管理-页面访问", "permission:view", "权限管理", "PAGE", "60"},
            {"角色管理-页面访问", "role:view", "权限管理", "PAGE", "61"},
            {"角色管理-创建", "role:create", "权限管理", "BUTTON", "62"},
            {"角色管理-编辑", "role:edit", "权限管理", "BUTTON", "63"},
            {"角色管理-删除", "role:delete", "权限管理", "BUTTON", "64"},
            {"权限分配-编辑", "permission:assign", "权限管理", "BUTTON", "65"},
            // 系统管理
            {"邮件模板-页面访问", "email-template:view", "系统管理", "PAGE", "70"},
            {"邮件模板-创建", "email-template:create", "系统管理", "BUTTON", "71"},
            {"邮件模板-编辑", "email-template:edit", "系统管理", "BUTTON", "72"},
            {"邮件模板-删除", "email-template:delete", "系统管理", "BUTTON", "73"},
            {"用户管理-页面访问", "user:view", "权限管理", "PAGE", "66"},
            {"用户管理-创建", "user:create", "权限管理", "BUTTON", "67"},
            {"用户管理-编辑", "user:edit", "权限管理", "BUTTON", "68"},
            {"用户管理-删除", "user:delete", "权限管理", "BUTTON", "69"},
            {"AI设置-页面访问", "ai-settings:view", "系统管理", "PAGE", "90"},
            {"AI设置-创建", "ai-settings:create", "系统管理", "BUTTON", "91"},
            {"AI设置-编辑", "ai-settings:edit", "系统管理", "BUTTON", "92"},
            {"AI设置-删除", "ai-settings:delete", "系统管理", "BUTTON", "93"},
            {"AI设置-启用禁用", "ai-settings:toggle", "系统管理", "BUTTON", "94"},
            {"AI设置-设置默认", "ai-settings:set-default", "系统管理", "BUTTON", "95"},
            {"AI设置-测试连通性", "ai-settings:test", "系统管理", "BUTTON", "96"},
            {"系统设置-页面访问", "settings:view", "系统管理", "PAGE", "100"},
            {"系统设置-基础设置", "settings:basic", "系统管理", "BUTTON", "110"},
            {"系统设置-安全配置", "settings:security", "系统管理", "BUTTON", "111"},
            {"系统设置-JWT配置", "settings:jwt", "系统管理", "BUTTON", "112"},
            {"系统设置-Mock配置", "settings:mock", "系统管理", "BUTTON", "113"},
            {"系统设置-公告管理", "settings:announcement", "系统管理", "BUTTON", "114"},
            {"系统设置-系统信息", "settings:system", "系统管理", "PAGE", "115"},
            {"系统设置-页脚设置", "settings:footer", "系统管理", "BUTTON", "116"},
            {"系统设置-注册设置", "settings:registration", "系统管理", "BUTTON", "117"},
            {"运维与监控-页面访问", "ops:view", "系统管理", "PAGE", "101"},
            {"运维与监控-备份导出", "ops:backup", "系统管理", "BUTTON", "102"},
            {"运维与监控-数据恢复", "ops:restore", "系统管理", "BUTTON", "103"},
            // 请求录制与回放
            {"录制回放-页面访问", "record-replay:view", "业务管理", "PAGE", "52"},
            {"录制回放-执行回放", "record-replay:replay", "业务管理", "BUTTON", "53"},
            // AI 服务管理
            {"AI服务管理-页面访问", "ai-service:view", "系统管理", "PAGE", "54"},
            // AI 用户自助（订阅管理）
            {"AI订阅-页面访问", "ai-subscription:view", "AI用户自助", "PAGE", "118"},
            {"AI订阅-订阅管理", "ai-subscription:subscribe", "AI用户自助", "BUTTON", "119"},
            {"AI订阅-密钥管理", "ai-subscription:key-manage", "AI用户自助", "BUTTON", "120"},
        };

        String valuesClause = "VALUES (?, ?, ?, ?, ?, " + now + ", " + now + ")";
        String sql = dialect.buildInsertOrIgnoreFull("t_permission",
            "(name, code, group_name, type, sort_order, create_time, update_time)", "code", valuesClause);
        for (String[] perm : perms) {
            try {
                jdbcTemplate.update(sql, perm[0], perm[1], perm[2], perm[3], Integer.parseInt(perm[4]));
            } catch (Exception e) {
                log.warn("插入权限失败: {} - {}", perm[1], e.getMessage());
            }
        }

        // 修正已有数据：将用户管理权限的 group_name 从"系统管理"更正为"权限管理"
        try {
            int updated = jdbcTemplate.update(
                "UPDATE t_permission SET group_name = '权限管理' WHERE code IN ('user:view', 'user:create', 'user:edit', 'user:delete') AND group_name = '系统管理'"
            );
            if (updated > 0) {
                log.info("已修正 {} 条用户管理权限的 group_name 从'系统管理'到'权限管理'", updated);
            }
        } catch (Exception e) {
            log.warn("修正用户管理权限 group_name 失败: {}", e.getMessage());
        }

        // 将系统设置子模块权限同步赋予已有 settings:view 权限的角色
        syncPermissionsToRoles("settings:view", new String[]{
            "settings:basic", "settings:security", "settings:jwt", "settings:mock",
            "settings:announcement", "settings:system", "settings:footer", "settings:registration"
        });

        // 将 AI 设置操作权限同步赋予已有 ai-settings:view 权限的角色
        syncPermissionsToRoles("ai-settings:view", new String[]{
            "ai-settings:create", "ai-settings:edit", "ai-settings:delete",
            "ai-settings:toggle", "ai-settings:set-default", "ai-settings:test"
        });

        // 将运维与监控权限同步赋予已有 settings:view 权限的角色
        syncPermissionsToRoles("settings:view", new String[]{
            "ops:view", "ops:backup", "ops:restore"
        });

        // 将录制回放权限同步赋予已有 api:view 权限的角色（已迁移到业务管理分组）
        syncPermissionsToRoles("api:view", new String[]{
            "record-replay:view", "record-replay:replay"
        });

        // 将 AI 服务管理权限同步赋予已有 settings:view 权限的角色
        syncPermissionsToRoles("settings:view", new String[]{
            "ai-service:view"
        });
    }

    /**
     * 将一组权限同步赋予已拥有源权限的角色
     */
    private void syncPermissionsToRoles(String sourceCode, String[] targetCodes) {
        for (String targetCode : targetCodes) {
            try {
                int affected = jdbcTemplate.update(
                    "INSERT INTO t_role_permission (role_id, permission_id) " +
                    "SELECT DISTINCT rp.role_id, (SELECT id FROM t_permission WHERE code = ?) " +
                    "FROM t_role_permission rp " +
                    "INNER JOIN t_permission p ON rp.permission_id = p.id " +
                    "WHERE p.code = ? " +
                    "AND (SELECT id FROM t_permission WHERE code = ?) IS NOT NULL " +
                    "AND NOT EXISTS (SELECT 1 FROM t_role_permission rp2 " +
                        "WHERE rp2.role_id = rp.role_id AND rp2.permission_id = (SELECT id FROM t_permission WHERE code = ?))",
                    targetCode, sourceCode, targetCode, targetCode
                );
                if (affected > 0) {
                    log.info("已同步 {} 权限到 {} 个拥有 {} 的角色", targetCode, affected, sourceCode);
                }
            } catch (Exception e) {
                log.warn("同步 {} 权限失败（可能已存在）: {}", targetCode, e.getMessage());
            }
        }
    }
}
