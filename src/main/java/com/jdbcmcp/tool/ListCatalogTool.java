package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;

/**
 * list_catalogs 工具：列出当前数据源可用的所有 Catalog。
 * 对应 JDBC {@link DatabaseMetaData#getCatalogs()}。
 */
@Slf4j
public class ListCatalogTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "list_catalogs";
    private static final String TOOL_DESCRIPTION =
            "List all available catalogs in the database. " +
                    "A catalog is a logical namespace at the top level of the database hierarchy. " +
                    "Returns the result in Markdown format.";

    private ListCatalogTool(DriverManager connectionManager) {
        super(connectionManager);
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        ListCatalogTool tool = new ListCatalogTool(connectionManager);
        var schema = new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        DatabaseMetaData dbMeta = getConnectionManager().getConnection().getMetaData();

        try (ResultSet catalogs = dbMeta.getCatalogs()) {
            return ResultFormatter.successResult(ResultFormatter.format(catalogs));
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Failed to list catalogs: {}", e.getMessage(), e);
    }
}
