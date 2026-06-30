package com.jdbcmcp.formatter;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML 表格流式构建器。
 * 支持定义带元数据属性的表头（data-type、data-length），
 * 自动处理 HTML 转义和 NULL 值渲染（<i>NULL</i>）。
 */
public class HtmlTableBuilder {

    private final List<ColumnDef> columns      = new ArrayList<>();
    private final StringBuilder   rows         = new StringBuilder();
    private       boolean         headerClosed = false;

    /**
     * 构建完整的 HTML 表格字符串。
     * @return HTML table 字符串
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\">");

        // 表头
        sb.append("<thead><tr>");
        for (ColumnDef col : columns) {
            sb.append("<th data-type=\"").append(ResultFormatter.escapeHtml(col.type))
                    .append("\" data-length=\"").append(col.length)
                    .append("\">").append(ResultFormatter.escapeHtml(col.name))
                    .append("</th>");
        }
        sb.append("</tr></thead>");

        // 表体
        sb.append("<tbody>");
        sb.append(rows);
        sb.append("</tbody></table>");

        return sb.toString();
    }

    /**
     * 添加一个表头列定义。
     * @param name   列名
     * @param type   数据类型（如 VARCHAR、INT）
     * @param length 显示长度
     * @return this
     */
    public HtmlTableBuilder header(String name, String type, int length) {
        columns.add(new ColumnDef(name, type, length));
        return this;
    }

    /**
     * 添加一行数据。
     * NULL 值自动渲染为 <i>NULL</i>，非 NULL 值自动进行 HTML 转义。
     * @param values 各列的值，数量应与表头列数一致
     * @return this
     */
    public HtmlTableBuilder row(Object... values) {
        if (!headerClosed) {
            headerClosed = true;
        }
        rows.append("<tr>");
        for (Object value : values) {
            if (value == null) {
                rows.append("<td><i>NULL</i></td>");
            } else {
                rows.append("<td>").append(ResultFormatter.escapeHtml(value.toString())).append("</td>");
            }
        }
        rows.append("</tr>");
        return this;
    }

    private record ColumnDef(String name, String type, int length) {
    }
}
