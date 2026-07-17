/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.carolcoral.apiserver.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 系统备份与恢复服务 — 一键备份/恢复 Mock 配置
 * <p>
 * 备份范围：所有业务配置表（排除日志/验证码等临时数据）
 * <ul>
 *   <li>用户、角色、权限</li>
 *   <li>项目、API、响应、请求参数</li>
 *   <li>系统配置、AI配置、邮件配置/模板</li>
 *   <li>代码模板、公告</li>
 * </ul>
 * 不备份：请求日志(t_request_log)、AI调用日志(t_ai_call_log)、验证码(t_verification_code)
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Service
public class SystemBackupService {

    private static final Logger log = LoggerFactory.getLogger(SystemBackupService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    // 备份包含的表（按恢复顺序排列，确保外键依赖正确）
    private static final String[] BACKUP_TABLES = {
            "t_user",
            "t_role",
            "t_permission",
            "t_role_permission",
            "t_system_config",
            "t_ai_config",
            "t_email_template",
            "t_email_config",
            "t_project",
            "t_project_member",
            "t_mock_api",
            "t_mock_response",
            "t_response_request_param",
            "t_custom_code_template",
            "t_system_announcement"
    };

    public SystemBackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 备份结果 DTO
     */
    public static class BackupResult {
        private boolean success;
        private String filename;
        private long sizeBytes;
        private int tableCount;
        private Map<String, Integer> tableRowCounts = new LinkedHashMap<>();
        private String backupTime;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public Map<String, Integer> getTableRowCounts() { return tableRowCounts; }
        public void setTableRowCounts(Map<String, Integer> tableRowCounts) { this.tableRowCounts = tableRowCounts; }
        public String getBackupTime() { return backupTime; }
        public void setBackupTime(String backupTime) { this.backupTime = backupTime; }
    }

    /**
     * 恢复结果 DTO
     */
    public static class RestoreResult {
        private boolean success;
        private int tablesRestored;
        private int totalRowsRestored;
        private String errorMessage;
        private List<String> details = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getTablesRestored() { return tablesRestored; }
        public void setTablesRestored(int tablesRestored) { this.tablesRestored = tablesRestored; }
        public int getTotalRowsRestored() { return totalRowsRestored; }
        public void setTotalRowsRestored(int totalRowsRestored) { this.totalRowsRestored = totalRowsRestored; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public List<String> getDetails() { return details; }
        public void setDetails(List<String> details) { this.details = details; }
    }

    /**
     * 创建全量备份
     *
     * @return 备份 JSON 字符串
     */
    public String createBackup() {
        JSONObject backup = new JSONObject();
        backup.put("version", "2.4.0");
        backup.put("backupTime", LocalDateTime.now().format(DTF));
        backup.put("type", "full");
        backup.put("description", "API Server 全量配置备份");

        JSONObject tables = new JSONObject();
        int totalRows = 0;

        for (String table : BACKUP_TABLES) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
                JSONArray tableData = new JSONArray();
                for (Map<String, Object> row : rows) {
                    JSONObject rowObj = new JSONObject();
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        Object value = entry.getValue();
                        // 处理日期时间类型
                        if (value instanceof LocalDateTime) {
                            value = ((LocalDateTime) value).format(DTF);
                        } else if (value instanceof java.sql.Timestamp) {
                            value = value.toString();
                        }
                        rowObj.put(entry.getKey(), value);
                    }
                    tableData.add(rowObj);
                }
                tables.put(table, tableData);
                totalRows += rows.size();
                log.debug("备份表 {}: {} 行", table, rows.size());
            } catch (Exception e) {
                log.warn("备份表 {} 失败: {}", table, e.getMessage());
                tables.put(table, new JSONArray());
            }
        }

        backup.put("tables", tables);
        log.info("备份完成: {} 个表, {} 行数据", BACKUP_TABLES.length, totalRows);
        return backup.toJSONString();
    }

    /**
     * 创建备份并返回结果信息
     */
    public BackupResult createBackupWithInfo() {
        BackupResult result = new BackupResult();
        try {
            String json = createBackup();
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            result.setSuccess(true);
            result.setSizeBytes(bytes.length);
            result.setTableCount(BACKUP_TABLES.length);
            result.setBackupTime(LocalDateTime.now().format(DTF));

            // 统计每张表的行数
            JSONObject backupObj = JSON.parseObject(json);
            JSONObject tablesObj = backupObj.getJSONObject("tables");
            for (String table : BACKUP_TABLES) {
                JSONArray arr = tablesObj.getJSONArray(table);
                result.getTableRowCounts().put(table, arr != null ? arr.size() : 0);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            result.setFilename("api-server-backup-" + timestamp + ".json");

        } catch (Exception e) {
            log.error("创建备份失败: {}", e.getMessage(), e);
            result.setSuccess(false);
        }
        return result;
    }

    /**
     * 从备份 JSON 恢复数据
     *
     * @param backupJson 备份 JSON 字符串
     * @param mode       恢复模式: "merge" 合并 / "replace" 先清空再恢复
     * @return 恢复结果
     */
    @Transactional
    public RestoreResult restoreFromBackup(String backupJson, String mode) {
        RestoreResult result = new RestoreResult();

        try {
            JSONObject backup = JSON.parseObject(backupJson);
            String backupVersion = backup.getString("version");
            String backupTime = backup.getString("backupTime");

            if (!backup.containsKey("tables")) {
                result.setSuccess(false);
                result.setErrorMessage("备份文件格式无效：缺少 tables 字段");
                return result;
            }

            JSONObject tables = backup.getJSONObject("tables");
            boolean isReplace = "replace".equalsIgnoreCase(mode);

            int totalRows = 0;
            int tablesRestored = 0;

            // 如果 replace 模式，从后往前清空（避免外键约束冲突）
            if (isReplace) {
                for (int i = BACKUP_TABLES.length - 1; i >= 0; i--) {
                    try {
                        int deleted = jdbcTemplate.update("DELETE FROM " + BACKUP_TABLES[i]);
                        result.getDetails().add("清空表 " + BACKUP_TABLES[i] + ": " + deleted + " 行");
                    } catch (Exception e) {
                        log.warn("清空表 {} 失败: {}", BACKUP_TABLES[i], e.getMessage());
                    }
                }
            }

            // 按依赖顺序恢复
            for (String table : BACKUP_TABLES) {
                JSONArray rows = tables.getJSONArray(table);
                if (rows == null || rows.isEmpty()) {
                    continue;
                }

                try {
                    int restored = 0;
                    for (int i = 0; i < rows.size(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        restoreRow(table, row, isReplace);
                        restored++;
                    }
                    result.getDetails().add("恢复表 " + table + ": " + restored + " 行");
                    totalRows += restored;
                    tablesRestored++;
                } catch (Exception e) {
                    log.error("恢复表 {} 失败: {}", table, e.getMessage());
                    result.getDetails().add("恢复表 " + table + " 失败: " + e.getMessage());
                }
            }

            result.setSuccess(true);
            result.setTablesRestored(tablesRestored);
            result.setTotalRowsRestored(totalRows);

            log.info("恢复完成: {} 个表, {} 行数据 (备份时间: {}, 模式: {})",
                    tablesRestored, totalRows, backupTime, mode);

        } catch (Exception e) {
            log.error("恢复失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("恢复失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 恢复单行数据
     */
    private void restoreRow(String table, JSONObject row, boolean isReplace) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();

            // 跳过 null 值（让数据库使用默认值）
            if (val == null) continue;

            columns.add(col);
            placeholders.add("?");
            values.add(val);
        }

        if (columns.isEmpty()) return;

        String sql;
        if (isReplace) {
            // INSERT OR REPLACE / INSERT INTO ... ON DUPLICATE KEY UPDATE
            sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", placeholders) + ")";
        } else {
            // INSERT OR IGNORE
            sql = "INSERT OR IGNORE INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", placeholders) + ")";
        }

        try {
            jdbcTemplate.update(sql, values.toArray());
        } catch (Exception e) {
            log.debug("插入行失败 (表={}): {}", table, e.getMessage());
        }
    }

    /**
     * 获取备份信息（不生成完整备份，仅返回元数据）
     */
    public Map<String, Object> getBackupInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tables", BACKUP_TABLES.length);

        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            try {
                int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                rowCounts.put(table, count);
            } catch (Exception e) {
                rowCounts.put(table, -1);
            }
        }
        info.put("rowCounts", rowCounts);
        return info;
    }
}
