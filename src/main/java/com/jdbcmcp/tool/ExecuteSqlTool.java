package com.jdbcmcp.tool;

import com.jdbcmcp.config.DatasourceConfig;
import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.interceptor.SqlInterceptor;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * execute_sql 工具：执行 Agent 传入的 SQL 语句，返回 HTML Table 格式结果。
 */
public class ExecuteSqlTool {

    private static final Logger log = LoggerFactory.getLogger(ExecuteSqlTool.class);

    private static final String TOOL_NAME        = "execute_sql";
    private static final String TOOL_DESCRIPTION =
            "Execute a SQL statement on the configured datasource and return results as an HTML table. " +
                    "For SELECT queries, the result includes column metadata (type and length) in the header. " +
                    "In read-only mode, DML and DDL statements are rejected.";

    private final DriverManager    connectionManager;
    private final SqlInterceptor   interceptor;
    private final DatasourceConfig config;

    public ExecuteSqlTool(DriverManager connectionManager,
                          SqlInterceptor interceptor,
                          DatasourceConfig config) {
        this.connectionManager = connectionManager;
        this.interceptor = interceptor;
        this.config = config;
    }

    /**
     * 创建 MCP Tool 规格定义
     */
    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager,
                                                                 SqlInterceptor interceptor,
                                                                 DatasourceConfig config) {
        ExecuteSqlTool tool = new ExecuteSqlTool(connectionManager, interceptor, config);

        var schema = new McpSchema.JsonSchema("object",
                Map.of("sql", Map.of("type", "string", "description", "The SQL statement to execute")),
                List.of("sql"), null, null, null);

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(schema)
                        .build())
                .callHandler((exchange, request) -> tool.handle(request))
                .build();
    }

    private McpSchema.CallToolResult handle(McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments();
        String sql = (String) arguments.get("sql");

        if (sql == null || sql.isBlank()) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Error: SQL statement is empty")))
                    .isError(true)
                    .build();
        }

        // 安全拦截检查
        String rejection = interceptor.check(sql);
        if (rejection != null) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Error: " + rejection)))
                    .isError(true)
                    .build();
        }

        int maxRows = config.getMaxRows();

        try (Connection conn = connectionManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // 设置最大返回行数
                stmt.setMaxRows(maxRows);

                boolean hasResultSet = stmt.execute(sql);

                if (hasResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        // 应用层行数计数保护
                        String htmlTable = com.jdbcmcp.formatter.ResultFormatter.format(rs, maxRows);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(htmlTable)))
                                .isError(false)
                                .build();
                    }
                } else {
                    int updateCount = stmt.getUpdateCount();
                    return McpSchema.CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent(
                                    "Statement executed successfully. Rows affected: " + updateCount)))
                            .isError(false)
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("SQL execution failed: {}", e.getMessage(), e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }
}
