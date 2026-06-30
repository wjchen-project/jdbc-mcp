package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;

/**
 * get_table_schema 工具：获取指定表的列结构、类型等元数据。
 * 对应 JDBC {@link DatabaseMetaData#getColumns(String, String, String, String)}。
 * 支持通过 catalog、schema、table 参数精确筛选目标表。
 */
@Slf4j
public class GetTableSchemaTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "get_table_schema";
    private static final String TOOL_DESCRIPTION =
            "Get the column schema metadata for a specific table, including column name, data type, " +
                    "type name, column size, nullable, and remarks. " +
                    "Supports optional catalog and schema parameters to narrow down the target table. " +
                    "Returns the result as an HTML table.";

    private GetTableSchemaTool(DriverManager connectionManager) {
        super(connectionManager);
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        GetTableSchemaTool tool = new GetTableSchemaTool(connectionManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of(
                        "catalog", Map.of("type", "string",
                                "description", "Optional catalog name to narrow the search. If not specified, matches all catalogs."),
                        "schema", Map.of("type", "string",
                                "description", "Optional schema name to narrow the search. If not specified, matches all schemas."),
                        "table", Map.of("type", "string",
                                "description", "Table name pattern (supports SQL LIKE wildcards). If not specified, matches all tables.")
                ),
                null, null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        Map<String, Object> arguments = request.arguments();
        String catalog = getOptionalString(arguments, "catalog");
        String schemaPattern = getOptionalString(arguments, "schema");
        String tablePattern = getOptionalString(arguments, "table");
        if (tablePattern == null) tablePattern = "%";

        try (Connection conn = getConnectionManager().getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();

            // 获取匹配的表，再用表实际所属的 catalog/schema 精确查询列信息
            try (ResultSet tables = dbMeta.getTables(catalog, schemaPattern, tablePattern, new String[]{"TABLE", "VIEW"})) {
                // 取第一个匹配表的列信息
                if (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableCatalog = tables.getString("TABLE_CAT");
                    String tableSchema = tables.getString("TABLE_SCHEM");

                    try (ResultSet columns = dbMeta.getColumns(tableCatalog, tableSchema, tableName, "%")) {
                        return ResultFormatter.successResult(ResultFormatter.format(columns));
                    }
                }
            }

            // 未匹配到任何表，返回空结果
            return ResultFormatter.successResult("No matching table found.");
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Failed to get table schema: {}", e.getMessage(), e);
    }
}
