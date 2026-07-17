/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据库方言提供器
 * 根据当前使用的数据库类型，返回适配的 SQL 片段
 * 支持 SQLite / MySQL / PostgreSQL
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Component
public class DatabaseDialectProvider {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseDialectProvider.class);

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    /**
     * 数据库类型枚举
     */
    public enum DbType {
        SQLITE, MYSQL, POSTGRESQL, UNKNOWN
    }

    /**
     * 检测当前数据库类型
     */
    public DbType detectDbType() {
        if (datasourceUrl == null) return DbType.SQLITE;
        String url = datasourceUrl.toLowerCase();
        if (url.contains("sqlite")) return DbType.SQLITE;
        if (url.contains("mysql")) return DbType.MYSQL;
        if (url.contains("postgresql")) return DbType.POSTGRESQL;
        return DbType.UNKNOWN;
    }

    /**
     * 获取当前时间戳的 SQL 表达式
     */
    public String nowExpression() {
        return switch (detectDbType()) {
            case MYSQL, POSTGRESQL -> "NOW()";
            default -> "datetime('now')"; // SQLite
        };
    }

    /**
     * 日期时间列类型
     * MySQL: DATETIME, PostgreSQL: TIMESTAMP, SQLite: DATETIME（无类型约束）
     */
    public String dateTimeType() {
        return switch (detectDbType()) {
            case POSTGRESQL -> "TIMESTAMP";
            default -> "DATETIME"; // MySQL / SQLite
        };
    }

    /**
     * 将 epoch 毫秒列格式化为日期字符串（替代 SQLite strftime）
     * SQLite:  strftime('%Y-%m-%d', col / 1000, 'unixepoch')
     * MySQL:   DATE_FORMAT(FROM_UNIXTIME(col / 1000), '%Y-%m-%d')
     * PG:      TO_CHAR(col, 'YYYY-MM-DD')  — 注意：PostgreSQL 中该列是 timestamp 类型，直接用 TO_CHAR
     *
     * @param columnExpr 列表达式（SQLite 中是 epoch 毫秒整数，PostgreSQL 中是 timestamp）
     * @param strftimePattern SQLite strftime 格式字符串（%Y/%m/%d/%H/%M/%S）
     */
    public String formatEpochMillisToDate(String columnExpr, String strftimePattern) {
        return switch (detectDbType()) {
            case MYSQL -> "DATE_FORMAT(FROM_UNIXTIME(" + columnExpr + " / 1000), '"
                + strftimeToMysql(strftimePattern) + "')";
            case POSTGRESQL -> "TO_CHAR(" + columnExpr + ", '"
                + strftimeToPg(strftimePattern) + "')";
            default -> "strftime('" + strftimePattern + "', " + columnExpr + " / 1000, 'unixepoch')"; // SQLite
        };
    }

    /**
     * 将日期时间列格式化为日期字符串（替代 SQLite strftime，用于原生 DATETIME/TIMESTAMP 列）
     * SQLite:  strftime('%Y-%m', col)
     * MySQL:   DATE_FORMAT(col, '%Y-%m')
     * PG:      TO_CHAR(col, 'YYYY-MM')
     *
     * @param columnExpr 日期时间列名
     * @param strftimePattern SQLite strftime 格式字符串
     */
    public String formatDateTimeToDate(String columnExpr, String strftimePattern) {
        return switch (detectDbType()) {
            case MYSQL -> "DATE_FORMAT(" + columnExpr + ", '"
                + strftimeToMysql(strftimePattern) + "')";
            case POSTGRESQL -> "TO_CHAR(" + columnExpr + ", '"
                + strftimeToPg(strftimePattern) + "')";
            default -> "strftime('" + strftimePattern + "', " + columnExpr + ")"; // SQLite
        };
    }

    /**
     * 判断当前数据库的时间列是否为原生类型（TIMESTAMP/DATETIME 而非 epoch 毫秒整数）
     * PostgreSQL: true（timestamp 类型）
     * MySQL: true（DATETIME 类型）
     * SQLite: false（epoch 毫秒整数存储）
     */
    public boolean isNativeDateTimeColumn() {
        return detectDbType() == DbType.POSTGRESQL || detectDbType() == DbType.MYSQL;
    }

    /**
     * 将 strftime 格式转换为 MySQL DATE_FORMAT 格式
     * strftime %M=分钟 → MySQL %i
     */
    private String strftimeToMysql(String pattern) {
        return pattern.replace("%M", "%i");
    }

    /**
     * 将 strftime 格式转换为 PostgreSQL TO_CHAR 格式
     */
    private String strftimeToPg(String pattern) {
        return pattern
            .replace("%Y", "YYYY")
            .replace("%m", "MM")
            .replace("%d", "DD")
            .replace("%H", "HH24")
            .replace("%M", "MI")
            .replace("%S", "SS");
    }

    /**
     * 插入或忽略的 SQL 前缀
     * MySQL: INSERT IGNORE INTO
     * PostgreSQL: INSERT INTO ... ON CONFLICT DO NOTHING
     * SQLite: INSERT OR IGNORE INTO
     */
    public String insertOrIgnorePrefix() {
        return switch (detectDbType()) {
            case MYSQL -> "INSERT IGNORE INTO";
            case POSTGRESQL -> "INSERT INTO"; // 需要配合 ON CONFLICT 使用
            default -> "INSERT OR IGNORE INTO"; // SQLite
        };
    }

    /**
     * 获取 INSERT ON CONFLICT 后缀（仅 PostgreSQL 使用）
     * 对于 MySQL/SQLite 返回空字符串
     */
    public String onConflictSuffix(String conflictColumn) {
        return switch (detectDbType()) {
            case POSTGRESQL -> " ON CONFLICT (" + conflictColumn + ") DO NOTHING";
            default -> "";
        };
    }

    /**
     * 插入或忽略的完整 SQL 前缀（自动处理数据库方言差异）
     * <p>注意：PostgreSQL 的 ON CONFLICT 必须在 VALUES 之后，因此调用方需要
     * 使用 {@link #buildInsertOrIgnoreFull} 或自行将 ON CONFLICT 拼接在末尾。</p>
     *
     * @deprecated 使用 {@link #buildInsertOrIgnoreFull} 代替，PostgreSQL 语法要求 ON CONFLICT 在 VALUES 之后
     */
    @Deprecated
    public String buildInsertOrIgnore(String table, String columns, String conflictColumn) {
        return switch (detectDbType()) {
            case MYSQL -> "INSERT IGNORE INTO " + table + " " + columns + " ";
            case POSTGRESQL -> "INSERT INTO " + table + " " + columns + " "; // ON CONFLICT 需要放在 VALUES 后面
            default -> "INSERT OR IGNORE INTO " + table + " " + columns + " "; // SQLite
        };
    }

    /**
     * 构建完整的 INSERT OR IGNORE 语句（自动处理数据库方言差异）
     * <p>PostgreSQL: INSERT INTO table (cols) VALUES (...) ON CONFLICT (col) DO NOTHING
     * <br>MySQL: INSERT IGNORE INTO table (cols) VALUES (...)
     * <br>SQLite: INSERT OR IGNORE INTO table (cols) VALUES (...)</p>
     *
     * @param table          表名
     * @param columns        列名部分，如 "(name, code, create_time)"
     * @param conflictColumn 冲突列（仅 PostgreSQL 使用）
     * @param values         值部分，如 "VALUES ('admin', 'ROLE_ADMIN', NOW())"
     * @return 完整 SQL 语句
     */
    public String buildInsertOrIgnoreFull(String table, String columns, String conflictColumn, String values) {
        return switch (detectDbType()) {
            case MYSQL -> "INSERT IGNORE INTO " + table + " " + columns + " " + values;
            case POSTGRESQL -> "INSERT INTO " + table + " " + columns + " " + values
                + " ON CONFLICT (" + conflictColumn + ") DO NOTHING";
            default -> "INSERT OR IGNORE INTO " + table + " " + columns + " " + values; // SQLite
        };
    }

    /**
     * BOOLEAN 字面值（用于 DEFAULT 子句或 INSERT 值）
     * PostgreSQL: TRUE/FALSE; MySQL/SQLite: 1/0
     */
    public String booleanLiteral(boolean value) {
        return switch (detectDbType()) {
            case POSTGRESQL -> value ? "TRUE" : "FALSE";
            default -> value ? "1" : "0";
        };
    }

    /**
     * BOOLEAN 列类型定义（用于 CREATE TABLE 语句）
     * PostgreSQL: BOOLEAN; MySQL/SQLite: BOOLEAN (兼容)
     */
    public String booleanType() {
        return "BOOLEAN";
    }

    /**
     * CREATE TABLE 的 ID 列定义
     */
    public String idColumnDefinition() {
        return switch (detectDbType()) {
            case MYSQL -> "id BIGINT AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> "id BIGSERIAL PRIMARY KEY";
            default -> "id INTEGER PRIMARY KEY AUTOINCREMENT"; // SQLite
        };
    }

    /**
     * CREATE INDEX IF NOT EXISTS 语法
     */
    public String createIndexIfNotExists(String indexName, String table, String columns) {
        return switch (detectDbType()) {
            case MYSQL, POSTGRESQL ->
                "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")";
            default ->
                "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")";
        };
    }

    /**
     * 是否使用序列生成 ID（PostgreSQL）
     */
    public boolean usesSequenceForId() {
        return detectDbType() == DbType.POSTGRESQL;
    }

    /**
     * 是否为 SQLite
     */
    public boolean isSqlite() {
        return detectDbType() == DbType.SQLITE;
    }

    /**
     * 获取驱动类名
     */
    public String getDriverClassName() {
        return switch (detectDbType()) {
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            default -> "org.sqlite.JDBC";
        };
    }
}
