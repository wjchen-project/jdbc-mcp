package com.jdbcmcp.formatter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 结果集格式化器。
 * 将 ResultSet 转换为带有元数据属性的 HTML Table 字符串。
 * 表头 <th> 附带 data-type 和 data-length 属性。
 * NULL 值渲染为 <i>NULL</i>。
 */
public class ResultFormatter {

    /**
     * 将 ResultSet 格式化为 HTML Table
     *
     * @param rs        查询结果集
     * @param maxRows   最大返回行数
     * @return HTML table 字符串
     */
    public static String format(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\">");

        // 表头
        sb.append("<thead><tr>");
        for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnLabel(i);
            String columnType = meta.getColumnTypeName(i);
            int displaySize = meta.getColumnDisplaySize(i);

            sb.append("<th data-type=\"").append(escapeHtml(columnType))
              .append("\" data-length=\"").append(displaySize)
              .append("\">").append(escapeHtml(columnName))
              .append("</th>");
        }
        sb.append("</tr></thead>");

        // 表体
        sb.append("<tbody>");
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            sb.append("<tr>");
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                if (rs.wasNull()) {
                    sb.append("<td><i>NULL</i></td>");
                } else {
                    sb.append("<td>").append(escapeHtml(value.toString())).append("</td>");
                }
            }
            sb.append("</tr>");
            rowCount++;
        }
        sb.append("</tbody></table>");

        return sb.toString();
    }

    /**
     * 简单的 HTML 转义，防止 XSS 和格式错乱
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
