package com.jdbcmcp.formatter;

import io.modelcontextprotocol.spec.McpSchema;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * 结果集格式化器。
 * 将 ResultSet 转换为 Markdown 格式字符串，包含 # Schema 和 # Data 两个部分。
 * Schema 部分列出列名、类型、长度；Data 部分列出数据行。
 * NULL 值渲染为 <i>NULL</i>。
 */
public class ResultFormatter {

    /**
     * 将 ResultSet 格式化为 Markdown（无行数限制）。
     * 适用于元数据查询等结果集较小的场景。
     * @param rs 查询结果集
     * @return Markdown 字符串
     */
    public static String format(ResultSet rs) throws SQLException {
        return format(rs, Integer.MAX_VALUE);
    }

    /**
     * 将 ResultSet 格式化为 Markdown
     * @param rs      查询结果集
     * @param maxRows 最大返回行数
     * @return Markdown 字符串
     */
    public static String format(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        MarkdownTableBuilder builder = new MarkdownTableBuilder();

        // 表头：通过 ResultSetMetaData 动态获取列名、类型、长度
        for (int i = 1; i <= columnCount; i++) {
            builder.header(meta.getColumnLabel(i), meta.getColumnTypeName(i), meta.getColumnDisplaySize(i));
        }

        // 表体
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Object[] values = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                values[i] = rs.getObject(i + 1);
                if (rs.wasNull()) {
                    values[i] = null;
                }
            }
            builder.row(values);
            rowCount++;
        }

        return builder.build();
    }

    /**
     * 构建 MCP 成功响应
     * @param text 结果文本
     * @return CallToolResult（isError=false）
     */
    public static McpSchema.CallToolResult successResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(false)
                .build();
    }

    /**
     * 构建 MCP 错误响应
     * @param text 错误信息
     * @return CallToolResult（isError=true）
     */
    public static McpSchema.CallToolResult errorResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(true)
                .build();
    }

    /**
     * Markdown 转义，防止格式错乱。
     * 在 Markdown 表格中，管道符 | 会破坏表格结构，需转义。
     */
    public static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
