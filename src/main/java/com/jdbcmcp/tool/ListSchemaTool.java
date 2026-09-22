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
 * list_schemas 工具：列出当前数据源中指定 Catalog 下的所有 Schema。
 * 对应 JDBC {@link DatabaseMetaData#getSchemas(String, String)}。
 */
@Slf4j
public class ListSchemaTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "list_schemas";
    private static final String TOOL_DESCRIPTION =
            "List all schemas in the database, optionally filtered by catalog. " +
                    "A schema is a namespace within a catalog that contains tables and other objects. " +
                    "Returns the result in Markdown format.";

    private ListSchemaTool(DriverManager connectionManager) {
        super(connectionManager);
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        ListSchemaTool tool = new ListSchemaTool(connectionManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of(
                        "catalog", Map.of("type", "string",
                                "description", "Optional catalog name to filter schemas. If not specified, lists schemas from all catalogs.")
                ),
                null, null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        Map<String, Object> arguments = request.arguments();
        String catalog = getOptionalString(arguments, "catalog");

        try (Connection conn = getConnectionManager().getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();

            try (ResultSet schemas = dbMeta.getSchemas(catalog, null)) {
                return ResultFormatter.successResult(ResultFormatter.format(schemas));
            }
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Failed to list schemas: {}", e.getMessage(), e);
    }
}
