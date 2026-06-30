package com.jdbcmcp.interceptor;

import lombok.Getter;

import java.util.regex.Pattern;

/**
 * SQL 安全拦截器。
 * 当数据源配置为只读模式时，对 SQL 语句进行前置语法检查和关键字过滤。
 * 拦截 DML (INSERT, UPDATE, DELETE) 和 DDL (DROP, ALTER, TRUNCATE, CREATE) 语句。
 */
@Getter
public class SqlInterceptor {

    /**
     * 匹配 SQL 语句开头的修改性关键字。
     * 支持忽略前导空白和注释（-- 单行注释），匹配第一个实际关键字。
     */
    private static final Pattern DML_DDL_PATTERN = Pattern.compile(
            "^\\s*(?:--[^\\n]*\\n\\s*)*(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final boolean readOnly;

    public SqlInterceptor(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * 检查 SQL 是否允许执行。
     * 只读模式下，拒绝 DML 和 DDL 语句。
     * @param sql 待执行的 SQL 语句
     * @return null 表示允许执行；非 null 为拒绝原因
     */
    public String check(String sql) {
        if (!readOnly) {
            return null;
        }

        if (sql == null || sql.isBlank()) {
            return "SQL statement is empty";
        }

        var matcher = DML_DDL_PATTERN.matcher(sql.trim());
        if (matcher.find()) {
            String keyword = matcher.group(1).toUpperCase();
            return "Read-only mode: " + keyword + " statements are not allowed";
        }

        return null;
    }
}
