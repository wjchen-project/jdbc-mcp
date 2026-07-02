package com.jdbcmcp.formatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 表格流式构建器。
 * 输出格式分为两个部分：
 * - # Schema：列出列名、类型、长度
 * - # Data：列出数据行
 * NULL 值渲染为 <i>NULL</i>。
 */
public class MarkdownTableBuilder {

    private final List<ColumnDef> columns = new ArrayList<>();
    private final List<Object[]>  rows    = new ArrayList<>();

    /**
     * 添加一个表头列定义。
     * @param name   列名
     * @param type   数据类型（如 VARCHAR、INT）
     * @param length 显示长度
     * @return this
     */
    public MarkdownTableBuilder header(String name, String type, int length) {
        columns.add(new ColumnDef(name, type, length));
        return this;
    }

    /**
     * 添加一行数据。
     * NULL 值自动渲染为 <i>NULL</i>，非 NULL 值自动进行 Markdown 转义。
     * @param values 各列的值，数量应与表头列数一致
     * @return this
     */
    public MarkdownTableBuilder row(Object... values) {
        rows.add(values);
        return this;
    }

    /**
     * 构建完整的 Markdown 字符串，包含 # Schema 和 # Data 两个部分。
     * @return Markdown 字符串
     */
    public String build() {
        StringBuilder sb = new StringBuilder();

        // # Schema 部分
        sb.append("# Schema\n");
        sb.append("| Name | Type | Length |\n");
        sb.append("| --- | --- | --- |\n");
        for (ColumnDef col : columns) {
            sb.append("| ").append(ResultFormatter.escapeMarkdown(col.name))
              .append(" | ").append(ResultFormatter.escapeMarkdown(col.type))
              .append(" | ").append(col.length).append(" |\n");
        }

        // # Data 部分
        sb.append("\n# Data\n");
        // 数据表头：仅列名
        sb.append("| ");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(ResultFormatter.escapeMarkdown(columns.get(i).name));
            if (i < columns.size() - 1) sb.append(" | ");
        }
        sb.append(" |\n");

        // 分隔行
        sb.append("| ");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("---");
            if (i < columns.size() - 1) sb.append(" | ");
        }
        sb.append(" |\n");

        // 数据行
        for (Object[] row : rows) {
            sb.append("| ");
            for (int i = 0; i < row.length; i++) {
                if (row[i] == null) {
                    sb.append("<i>NULL</i>");
                } else {
                    sb.append(ResultFormatter.escapeMarkdown(row[i].toString()));
                }
                if (i < row.length - 1) sb.append(" | ");
            }
            sb.append(" |\n");
        }

        return sb.toString();
    }

    private record ColumnDef(String name, String type, int length) {
    }
}
