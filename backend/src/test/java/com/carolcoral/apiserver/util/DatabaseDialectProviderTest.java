/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import com.carolcoral.apiserver.util.DatabaseDialectProvider.DbType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseDialectProvider 单元测试：覆盖 SQLite / MySQL / PostgreSQL 三种方言分支
 *
 * @author carolcoral
 */
class DatabaseDialectProviderTest {

    private DatabaseDialectProvider provider(String url) {
        DatabaseDialectProvider provider = new DatabaseDialectProvider();
        ReflectionTestUtils.setField(provider, "datasourceUrl", url);
        return provider;
    }

    @Nested
    @DisplayName("数据库类型识别")
    class DetectDbType {

        @Test
        @DisplayName("SQLite URL 识别为 SQLITE")
        void detectsSqlite() {
            assertEquals(DbType.SQLITE, provider("jdbc:sqlite:./data/mock-server.db").detectDbType());
        }

        @Test
        @DisplayName("MySQL URL 识别为 MYSQL")
        void detectsMysql() {
            assertEquals(DbType.MYSQL, provider("jdbc:mysql://localhost:3306/api_server").detectDbType());
        }

        @Test
        @DisplayName("PostgreSQL URL 识别为 POSTGRESQL")
        void detectsPostgresql() {
            assertEquals(DbType.POSTGRESQL, provider("jdbc:postgresql://localhost:5432/api_server").detectDbType());
        }

        @Test
        @DisplayName("未知 URL 识别为 UNKNOWN")
        void detectsUnknown() {
            assertEquals(DbType.UNKNOWN, provider("jdbc:oracle:thin:@localhost:1521:xe").detectDbType());
        }

        @Test
        @DisplayName("URL 为 null 时回退 SQLite，空串视为 UNKNOWN")
        void nullUrlFallsBackToSqlite() {
            assertEquals(DbType.SQLITE, provider(null).detectDbType());
            assertEquals(DbType.UNKNOWN, provider("").detectDbType());
        }

        @Test
        @DisplayName("URL 大小写不敏感")
        void urlIsCaseInsensitive() {
            assertEquals(DbType.MYSQL, provider("JDBC:MYSQL://HOST/DB").detectDbType());
            assertEquals(DbType.POSTGRESQL, provider("JDBC:PostgreSQL://HOST/DB").detectDbType());
        }
    }

    @Nested
    @DisplayName("方言 SQL 片段")
    class DialectFragments {

        @Test
        @DisplayName("nowExpression 按方言返回")
        void nowExpression() {
            assertEquals("datetime('now')", provider("jdbc:sqlite:test.db").nowExpression());
            assertEquals("NOW()", provider("jdbc:mysql://h/db").nowExpression());
            assertEquals("NOW()", provider("jdbc:postgresql://h/db").nowExpression());
        }

        @Test
        @DisplayName("dateTimeType 按方言返回")
        void dateTimeType() {
            assertEquals("DATETIME", provider("jdbc:sqlite:test.db").dateTimeType());
            assertEquals("DATETIME", provider("jdbc:mysql://h/db").dateTimeType());
            assertEquals("TIMESTAMP", provider("jdbc:postgresql://h/db").dateTimeType());
        }

        @Test
        @DisplayName("booleanLiteral 按方言返回")
        void booleanLiteral() {
            assertEquals("1", provider("jdbc:sqlite:test.db").booleanLiteral(true));
            assertEquals("0", provider("jdbc:mysql://h/db").booleanLiteral(false));
            assertEquals("TRUE", provider("jdbc:postgresql://h/db").booleanLiteral(true));
            assertEquals("FALSE", provider("jdbc:postgresql://h/db").booleanLiteral(false));
        }

        @Test
        @DisplayName("booleanType 与实现无关，固定 BOOLEAN")
        void booleanType() {
            assertEquals("BOOLEAN", provider("jdbc:sqlite:test.db").booleanType());
        }

        @Test
        @DisplayName("idColumnDefinition 按方言返回")
        void idColumnDefinition() {
            assertEquals("id INTEGER PRIMARY KEY AUTOINCREMENT", provider("jdbc:sqlite:test.db").idColumnDefinition());
            assertEquals("id BIGINT AUTO_INCREMENT PRIMARY KEY", provider("jdbc:mysql://h/db").idColumnDefinition());
            assertEquals("id BIGSERIAL PRIMARY KEY", provider("jdbc:postgresql://h/db").idColumnDefinition());
        }

        @Test
        @DisplayName("getDriverClassName 按方言返回")
        void driverClassName() {
            assertEquals("org.sqlite.JDBC", provider("jdbc:sqlite:test.db").getDriverClassName());
            assertEquals("com.mysql.cj.jdbc.Driver", provider("jdbc:mysql://h/db").getDriverClassName());
            assertEquals("org.postgresql.Driver", provider("jdbc:postgresql://h/db").getDriverClassName());
            assertEquals("org.sqlite.JDBC", provider("jdbc:oracle:thin:@h:1:x").getDriverClassName());
        }

        @Test
        @DisplayName("usesSequenceForId 仅 PostgreSQL 为 true")
        void usesSequenceForId() {
            assertTrue(provider("jdbc:postgresql://h/db").usesSequenceForId());
            assertFalse(provider("jdbc:mysql://h/db").usesSequenceForId());
            assertFalse(provider("jdbc:sqlite:test.db").usesSequenceForId());
        }

        @Test
        @DisplayName("isSqlite 判定正确")
        void isSqlite() {
            assertTrue(provider("jdbc:sqlite:test.db").isSqlite());
            assertFalse(provider("jdbc:mysql://h/db").isSqlite());
        }

        @Test
        @DisplayName("isNativeDateTimeColumn 仅 MySQL/PostgreSQL 为 true")
        void isNativeDateTimeColumn() {
            assertFalse(provider("jdbc:sqlite:test.db").isNativeDateTimeColumn());
            assertTrue(provider("jdbc:mysql://h/db").isNativeDateTimeColumn());
            assertTrue(provider("jdbc:postgresql://h/db").isNativeDateTimeColumn());
        }

        @Test
        @DisplayName("createIndexIfNotExists 生成完整语句")
        void createIndexIfNotExists() {
            String sql = provider("jdbc:sqlite:test.db").createIndexIfNotExists("idx_a", "t_a", "col_a, col_b");
            assertEquals("CREATE INDEX IF NOT EXISTS idx_a ON t_a(col_a, col_b)", sql);
        }
    }

    @Nested
    @DisplayName("日期格式化表达式")
    class DateFormatting {

        @Test
        @DisplayName("formatEpochMillisToDate 按方言转换 strftime 占位符")
        void formatEpochMillisToDate() {
            String sqlite = provider("jdbc:sqlite:test.db").formatEpochMillisToDate("t.create_time", "%Y-%m-%d %H:%M:%S");
            assertTrue(sqlite.startsWith("strftime('"));
            assertTrue(sqlite.contains("unixepoch"));

            String mysql = provider("jdbc:mysql://h/db").formatEpochMillisToDate("t.create_time", "%Y-%m-%d %H:%M:%S");
            assertTrue(mysql.startsWith("DATE_FORMAT(FROM_UNIXTIME("));
            assertTrue(mysql.contains("%i"), "MySQL 的分钟占位符应转换为 %i");

            String pg = provider("jdbc:postgresql://h/db").formatEpochMillisToDate("t.create_time", "%Y-%m-%d %H:%M:%S");
            assertTrue(pg.startsWith("TO_CHAR("));
            assertTrue(pg.contains("YYYY-MM-DD HH24:MI:SS"));
        }

        @Test
        @DisplayName("formatDateTimeToDate 按方言生成表达式")
        void formatDateTimeToDate() {
            String sqlite = provider("jdbc:sqlite:test.db").formatDateTimeToDate("t.create_time", "%Y-%m");
            assertEquals("strftime('%Y-%m', t.create_time)", sqlite);

            String mysql = provider("jdbc:mysql://h/db").formatDateTimeToDate("t.create_time", "%Y-%m");
            assertEquals("DATE_FORMAT(t.create_time, '%Y-%m')", mysql);

            String pg = provider("jdbc:postgresql://h/db").formatDateTimeToDate("t.create_time", "%Y-%m");
            assertEquals("TO_CHAR(t.create_time, 'YYYY-MM')", pg);
        }
    }

    @Nested
    @DisplayName("INSERT OR IGNORE 语句构建")
    class InsertOrIgnore {

        @Test
        @DisplayName("insertOrIgnorePrefix 按方言返回")
        void insertOrIgnorePrefix() {
            assertEquals("INSERT OR IGNORE INTO", provider("jdbc:sqlite:test.db").insertOrIgnorePrefix());
            assertEquals("INSERT IGNORE INTO", provider("jdbc:mysql://h/db").insertOrIgnorePrefix());
            assertEquals("INSERT INTO", provider("jdbc:postgresql://h/db").insertOrIgnorePrefix());
        }

        @Test
        @DisplayName("onConflictSuffix 仅 PostgreSQL 返回子句")
        void onConflictSuffix() {
            assertEquals("", provider("jdbc:sqlite:test.db").onConflictSuffix("code"));
            assertEquals("", provider("jdbc:mysql://h/db").onConflictSuffix("code"));
            assertEquals(" ON CONFLICT (code) DO NOTHING", provider("jdbc:postgresql://h/db").onConflictSuffix("code"));
        }

        @Test
        @DisplayName("buildInsertOrIgnoreFull 生成三种方言语句")
        void buildInsertOrIgnoreFull() {
            String cols = "(name, code)";
            String values = "VALUES ('admin', 'A')";

            assertEquals("INSERT OR IGNORE INTO t_user (name, code) VALUES ('admin', 'A')",
                    provider("jdbc:sqlite:test.db").buildInsertOrIgnoreFull("t_user", cols, "code", values));
            assertEquals("INSERT IGNORE INTO t_user (name, code) VALUES ('admin', 'A')",
                    provider("jdbc:mysql://h/db").buildInsertOrIgnoreFull("t_user", cols, "code", values));
            assertEquals("INSERT INTO t_user (name, code) VALUES ('admin', 'A') ON CONFLICT (code) DO NOTHING",
                    provider("jdbc:postgresql://h/db").buildInsertOrIgnoreFull("t_user", cols, "code", values));
        }

        @Test
        @DisplayName("buildInsertOrIgnore（已废弃）保持向后兼容")
        @SuppressWarnings("deprecation")
        void deprecatedBuildInsertOrIgnore() {
            assertEquals("INSERT OR IGNORE INTO t_user (name) ",
                    provider("jdbc:sqlite:test.db").buildInsertOrIgnore("t_user", "(name)", "name"));
            assertEquals("INSERT IGNORE INTO t_user (name) ",
                    provider("jdbc:mysql://h/db").buildInsertOrIgnore("t_user", "(name)", "name"));
            assertEquals("INSERT INTO t_user (name) ",
                    provider("jdbc:postgresql://h/db").buildInsertOrIgnore("t_user", "(name)", "name"));
        }
    }
}
