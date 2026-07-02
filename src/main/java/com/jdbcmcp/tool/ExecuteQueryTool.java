package com.jdbcmcp.tool;

import com.jdbcmcp.config.DatasourceConfig;
import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import com.jdbcmcp.interceptor.QueryOnlyInterceptor;
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
 * execute_query 工具：执行只读查询 SQL，返回 Markdown 格式结果。
 * 该工具天然只读，即使数据源未配置 read_only，也仅允许 SELECT 查询。
 * 自动应用 maxRows 限制防止过量数据占用模型上下文。
 */
@Slf4j
public class ExecuteQueryTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "execute_query";
    private static final String TOOL_DESCRIPTION =
            "Execute a read-only SQL query (SELECT) on the configured datasource and return results in Markdown format. " +
                    "Only SELECT statements are allowed. The result is divided into # Schema (column name, type, length) " +
                    "and # Data sections. The number of returned rows is limited by the max_rows configuration (default 100).";

    private final DatasourceConfig config;

    private ExecuteQueryTool(DriverManager connectionManager, DatasourceConfig config) {
        super(connectionManager);
        this.config = config;
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager,
                                                                 DatasourceConfig config) {
        ExecuteQueryTool tool = new ExecuteQueryTool(connectionManager, config);
        var schema = new McpSchema.JsonSchema("object",
                Map.of("sql", Map.of("type", "string", "description", "The SELECT SQL query to execute")),
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

        // execute_query 天然只读，始终拒绝非 SELECT 语句
        String rejection = QueryOnlyInterceptor.check(sql);
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
                    // 理论上不会走到这里（已拦截非 SELECT），但做防御性处理
                    return ResultFormatter.errorResult("Error: execute_query only supports SELECT statements");
                }
            }
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Query execution failed: {}", e.getMessage(), e);
    }
}
