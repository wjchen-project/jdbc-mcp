package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * get_schema 工具：获取当前数据源的所有表结构、字段、类型等元数据。
 */
@Slf4j
public class GetSchemaTool {

    private static final String TOOL_NAME        = "get_schema";
    private static final String TOOL_DESCRIPTION =
            "Get the database schema metadata for the configured datasource, " +
                    "including all tables, columns, types, and constraints. " +
                    "Returns the result as an HTML table.";

    private final DriverManager connectionManager;

    public GetSchemaTool(DriverManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 创建 MCP Tool 规格定义
     */
    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        GetSchemaTool tool = new GetSchemaTool(connectionManager);

        var schema = new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(schema)
                        .build())
                .callHandler((exchange, request) -> tool.handle())
                .build();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private McpSchema.CallToolResult handle() {
        StringBuilder sb = new StringBuilder();

        try (Connection conn = connectionManager.getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();

            sb.append("<table border=\"1\">");
            sb.append("<thead><tr>");
            sb.append("<th data-type=\"VARCHAR\" data-length=\"256\">TABLE_NAME</th>");
            sb.append("<th data-type=\"VARCHAR\" data-length=\"256\">COLUMN_NAME</th>");
            sb.append("<th data-type=\"VARCHAR\" data-length=\"64\">DATA_TYPE</th>");
            sb.append("<th data-type=\"VARCHAR\" data-length=\"64\">TYPE_NAME</th>");
            sb.append("<th data-type=\"INT\" data-length=\"11\">COLUMN_SIZE</th>");
            sb.append("<th data-type=\"INT\" data-length=\"11\">NULLABLE</th>");
            sb.append("<th data-type=\"VARCHAR\" data-length=\"256\">REMARKS</th>");
            sb.append("</tr></thead>");

            sb.append("<tbody>");

            // 获取所有表
            try (ResultSet tables = dbMeta.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    // 获取该表的列信息
                    try (ResultSet columns = dbMeta.getColumns(null, null, tableName, "%")) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            int dataType = columns.getInt("DATA_TYPE");
                            String typeName = columns.getString("TYPE_NAME");
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            String nullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO";
                            String remarks = columns.getString("REMARKS");

                            sb.append("<tr>");
                            sb.append("<td>").append(escapeHtml(tableName)).append("</td>");
                            sb.append("<td>").append(escapeHtml(columnName)).append("</td>");
                            sb.append("<td>").append(dataType).append("</td>");
                            sb.append("<td>").append(escapeHtml(typeName)).append("</td>");
                            sb.append("<td>").append(columnSize).append("</td>");
                            sb.append("<td>").append(nullable).append("</td>");
                            sb.append("<td>").append(remarks != null ? escapeHtml(remarks) : "<i>NULL</i>").append("</td>");
                            sb.append("</tr>");
                        }
                    }
                }
            }

            sb.append("</tbody></table>");

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(sb.toString())))
                    .isError(false)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get schema: {}", e.getMessage(), e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }
}
