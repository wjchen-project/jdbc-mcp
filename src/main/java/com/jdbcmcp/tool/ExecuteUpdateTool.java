package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import com.jdbcmcp.interceptor.SqlInterceptor;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * execute_update 工具：执行数据修改 SQL（INSERT/UPDATE/DELETE/DDL），
 * 返回受影响行数。默认只读拦截，只有显式传入 --danger-allow-write 后才允许写操作。
 */
@Slf4j
public class ExecuteUpdateTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "execute_update";
    private static final String TOOL_DESCRIPTION =
            "Execute a data modification SQL statement (INSERT, UPDATE, DELETE, DDL) on the configured datasource. " +
                    "Returns the number of rows affected. Modification statements are rejected unless " +
                    "--danger-allow-write is explicitly enabled at startup.";

    private final SqlInterceptor interceptor;

    private ExecuteUpdateTool(DriverManager connectionManager,
                              SqlInterceptor interceptor) {
        super(connectionManager);
        this.interceptor = interceptor;
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager,
                                                                 SqlInterceptor interceptor) {
        ExecuteUpdateTool tool = new ExecuteUpdateTool(connectionManager, interceptor);
        var schema = new McpSchema.JsonSchema("object",
                Map.of("sql", Map.of("type", "string", "description", "The SQL statement to execute (INSERT, UPDATE, DELETE, DDL)")),
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

        // 安全拦截检查（未传入 --danger-allow-write 时拒绝修改操作）
        String rejection = interceptor.check(sql);
        if (rejection != null) {
            return ResultFormatter.errorResult("Error: " + rejection);
        }

        try (Connection conn = getConnectionManager().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                //noinspection SqlSourceToSinkFlow
                boolean hasResultSet = stmt.execute(sql);

                if (hasResultSet) {
                    // execute_update 不支持 SELECT，但某些 DDL 可能返回结果集，做防御性关闭
                    stmt.getResultSet().close();
                    return ResultFormatter.successResult("Statement executed successfully.");
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
        log.error("Update execution failed: {}", e.getMessage(), e);
    }
}
