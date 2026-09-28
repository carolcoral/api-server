/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseMigration 的 OIDC 字段迁移单元测试。
 *
 * <p>回归背景：t_user 表在 OIDC（TDP 单点登录）功能引入前就已存在，而 Hibernate 的
 * {@code ddl-auto: update} 对 SQLite 方言不会补齐新增列，导致登录查询
 * {@code SELECT ... u1_0.oidc_sub ...} 报 {@code no such column: u1_0.oidc_sub}，
 * 前端只能看到「用户名或密码错误」。</p>
 *
 * <p>本测试用一个「旧结构」的内存 SQLite 库复现该场景，跑完迁移后断言 oidc_* 列与唯一索引都已存在。</p>
 *
 * @author carolcoral
 */
class DatabaseMigrationOidcTest {

    /**
     * 构造一个刻意缺少 oidc_* 列的 t_user 表，模拟存量数据库
     */
    private DataSource legacyDataSource(JdbcTemplate[] holder) throws Exception {
        File dbFile = File.createTempFile("legacy-oidc-", ".db");
        dbFile.deleteOnExit();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        holder[0] = jdbcTemplate;

        jdbcTemplate.execute("CREATE TABLE t_user ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username VARCHAR(50) NOT NULL,"
                + "password VARCHAR(255) NOT NULL,"
                + "role VARCHAR(20) NOT NULL,"
                + "enabled BOOLEAN NOT NULL DEFAULT 1,"
                + "create_time DATETIME,"
                + "update_time DATETIME)");
        return ds;
    }

    private DatabaseMigration migration(JdbcTemplate jdbcTemplate) {
        DatabaseMigration migration = new DatabaseMigration();
        ReflectionTestUtils.setField(migration, "jdbcTemplate", jdbcTemplate);
        DatabaseDialectProvider dialect = new DatabaseDialectProvider();
        ReflectionTestUtils.setField(dialect, "datasourceUrl", "jdbc:sqlite:./data/mock-server.db");
        ReflectionTestUtils.setField(migration, "dialect", dialect);
        return migration;
    }

    private List<String> columnsOf(JdbcTemplate jdbcTemplate, String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().map(r -> String.valueOf(r.get("name")).toLowerCase()).toList();
    }

    @Test
    @DisplayName("存量库缺少 oidc_* 列时，迁移会自动补齐并建立唯一索引")
    void addsOidcColumnsForLegacyDatabase() throws Exception {
        JdbcTemplate[] holder = new JdbcTemplate[1];
        legacyDataSource(holder);
        JdbcTemplate jdbcTemplate = holder[0];

        List<String> before = columnsOf(jdbcTemplate, "t_user");
        assertFalse(before.contains("oidc_sub"), "前置条件：旧库不应有 oidc_sub");

        // 执行迁移（SQLite 分支 + 通用分支）
        migration(jdbcTemplate).run();

        List<String> after = columnsOf(jdbcTemplate, "t_user");
        assertTrue(after.contains("oidc_sub"), "迁移后应存在 oidc_sub 列");
        assertTrue(after.contains("oidc_provider"), "迁移后应存在 oidc_provider 列");
        assertTrue(after.contains("oidc_account"), "迁移后应存在 oidc_account 列");

        // 唯一索引应已建立，且历史用户（oidc_sub 为 NULL）不受唯一约束影响
        List<Map<String, Object>> indexes =
                jdbcTemplate.queryForList("PRAGMA index_list(t_user)");
        assertTrue(indexes.stream().anyMatch(r -> "uk_user_oidc_sub".equals(String.valueOf(r.get("name")))),
                "迁移后应存在 uk_user_oidc_sub 唯一索引");

        jdbcTemplate.update("INSERT INTO t_user (username, password, role, enabled) VALUES ('u1','p','USER',1)");
        jdbcTemplate.update("INSERT INTO t_user (username, password, role, enabled) VALUES ('u2','p','USER',1)");
        Integer nullSubCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE oidc_sub IS NULL", Integer.class);
        assertEquals(2, nullSubCount, "唯一索引允许多个 NULL，历史用户不受影响");
    }

    @Test
    @DisplayName("迁移可重复执行（幂等），列已存在时跳过且不抛异常")
    void migrationIsIdempotent() throws Exception {
        JdbcTemplate[] holder = new JdbcTemplate[1];
        legacyDataSource(holder);
        JdbcTemplate jdbcTemplate = holder[0];

        DatabaseMigration migration = migration(jdbcTemplate);
        assertDoesNotThrow(() -> {
            migration.run();
            migration.run();
        });
        assertTrue(columnsOf(jdbcTemplate, "t_user").contains("oidc_sub"));
    }
}
