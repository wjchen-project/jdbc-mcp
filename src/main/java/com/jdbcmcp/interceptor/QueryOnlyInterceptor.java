package com.jdbcmcp.interceptor;

import java.util.regex.Pattern;

/**
 * 只读查询拦截器。
 * 始终拒绝非 SELECT 语句（DML/DDL），用于 execute_query 工具。
 * 与 {@link SqlInterceptor} 不同，该拦截器无条件生效，不依赖数据源的 read_only 配置。
 */
public class QueryOnlyInterceptor {

    /**
     * 匹配 SQL 语句开头的非 SELECT 关键字。
     * 支持忽略前导空白和注释（-- 单行注释），匹配第一个实际关键字。
     */
    private static final Pattern NON_SELECT_PATTERN = Pattern.compile(
            "^\\s*(?:--[^\\n]*\\n\\s*)*(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 检查 SQL 是否为只读查询。
     * 仅允许 SELECT 语句，拒绝所有 DML 和 DDL。
     * @param sql 待执行的 SQL 语句
     * @return null 表示允许执行；非 null 为拒绝原因
     */
    public static String check(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL statement is empty";
        }

        var matcher = NON_SELECT_PATTERN.matcher(sql.trim());
        if (matcher.find()) {
            String keyword = matcher.group(1).toUpperCase();
            return "execute_query only supports SELECT statements. " + keyword + " is not allowed";
        }

        return null;
    }
}
