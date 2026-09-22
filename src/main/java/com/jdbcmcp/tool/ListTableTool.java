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
 * list_tables 工具：列出当前数据源中指定 Catalog/Schema 下的所有表和视图。
 * 对应 JDBC {@link DatabaseMetaData#getTables(String, String, String, String[])}。
 */
@Slf4j
public class ListTableTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "list_tables";
    private static final String TOOL_DESCRIPTION =
            "List all tables and views in the database, optionally filtered by catalog, schema, and table name pattern. " +
                    "Returns the result in Markdown format with table type, schema, catalog, and remarks.";

    private ListTableTool(DriverManager connectionManager) {
        super(connectionManager);
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        ListTableTool tool = new ListTableTool(connectionManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of(
                        "catalog", Map.of("type", "string",
                                "description", "Optional catalog name to filter tables. If not specified, matches all catalogs."),
                        "schema", Map.of("type", "string",
                                "description", "Optional schema name pattern to filter tables. If not specified, matches all schemas."),
                        "table_pattern", Map.of("type", "string",
                                "description", "Optional table name pattern (supports SQL LIKE wildcards). If not specified, matches all tables.")
                ),
                null, null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        Map<String, Object> arguments = request.arguments();
        String catalog = getOptionalString(arguments, "catalog");
        String schemaPattern = getOptionalString(arguments, "schema");
        String tablePattern = getOptionalString(arguments, "table_pattern");
        if (tablePattern == null) tablePattern = "%";

        try (Connection conn = getConnectionManager().getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();

            try (ResultSet tables = dbMeta.getTables(catalog, schemaPattern, tablePattern, new String[]{"TABLE", "VIEW", "SYSTEM TABLE"})) {
                return ResultFormatter.successResult(ResultFormatter.format(tables));
            }
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Failed to list tables: {}", e.getMessage(), e);
    }
}
