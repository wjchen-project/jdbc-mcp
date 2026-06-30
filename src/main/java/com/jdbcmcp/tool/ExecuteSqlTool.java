package com.jdbcmcp.tool;

import com.jdbcmcp.config.DatasourceConfig;
import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import com.jdbcmcp.interceptor.SqlInterceptor;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * execute_sql 工具：执行 Agent 传入的 SQL 语句，返回 HTML Table 格式结果。
 */
@Slf4j
public class ExecuteSqlTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "execute_sql";
    private static final String TOOL_DESCRIPTION =
            "Execute a SQL statement on the configured datasource and return results as an HTML table. " +
                    "For SELECT queries, the result includes column metadata (type and length) in the header. " +
                    "In read-only mode, DML and DDL statements are rejected.";

    private final SqlInterceptor   interceptor;
    private final DatasourceConfig config;

    private ExecuteSqlTool(DriverManager connectionManager,
                           SqlInterceptor interceptor,
                           DatasourceConfig config) {
        super(connectionManager);
        this.interceptor = interceptor;
        this.config = config;
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager,
                                                                 SqlInterceptor interceptor,
                                                                 DatasourceConfig config) {
        ExecuteSqlTool tool = new ExecuteSqlTool(connectionManager, interceptor, config);
        var schema = new McpSchema.JsonSchema("object",
                Map.of("sql", Map.of("type", "string", "description", "The SQL statement to execute")),
                List.of("sql"), null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        Map<String, Object> arguments = request.arguments();
        String sql = (String) arguments.get("sql");

        if (sql == null || sql.isBlank()) {
            return ResultFormatter.errorResult("Error: SQL statement is empty");
        }

        // 安全拦截检查
        String rejection = interceptor.check(sql);
        if (rejection != null) {
            return ResultFormatter.errorResult("Error: " + rejection);
        }

        int maxRows = config.getMaxRows();

        try (Connection conn = getConnectionManager().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setMaxRows(maxRows);

                boolean hasResultSet = stmt.execute(sql);

                if (hasResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        return ResultFormatter.successResult(ResultFormatter.format(rs, maxRows));
                    }
                } else {
                    int updateCount = stmt.getUpdateCount();
                    return ResultFormatter.successResult(
                            "Statement executed successfully. Rows affected: " + updateCount);
                }
            }
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("SQL execution failed: {}", e.getMessage(), e);
    }
}
