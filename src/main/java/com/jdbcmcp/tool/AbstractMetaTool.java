package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.Map;

/**
 * 元数据查询工具的抽象基类。
 * 封装 MCP Tool 的通用创建逻辑和参数提取方法，
 * 子类只需实现 {@link #doHandle} 定义具体的元数据查询逻辑。
 */
abstract class AbstractMetaTool {

    private final DriverManager connectionManager;

    protected AbstractMetaTool(DriverManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 创建 MCP Tool 规格定义。
     * 子类通过参数提供工具名称、描述和输入 schema，框架自动注册 callHandler。
     */
    protected static McpServerFeatures.SyncToolSpecification buildSpecification(
            AbstractMetaTool tool,
            String toolName,
            String toolDescription,
            McpSchema.JsonSchema inputSchema) {

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema(inputSchema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        return tool.doHandle(request);
                    } catch (Exception e) {
                        tool.onError(e);
                        return ResultFormatter.errorResult("Error: " + e.getMessage());
                    }
                })
                .build();
    }

    /**
     * 从请求参数中提取可选字符串参数。
     */
    protected static String getOptionalString(Map<String, Object> arguments, String key) {
        return arguments.get(key) != null ? (String) arguments.get(key) : null;
    }

    /**
     * 子类实现具体的元数据查询逻辑。
     */
    protected abstract CallToolResult doHandle(CallToolRequest request) throws Exception;

    protected DriverManager getConnectionManager() {
        return connectionManager;
    }

    /**
     * 错误处理回调，子类可覆写以自定义日志。
     */
    protected void onError(Exception e) {
        // 默认空实现，子类按需覆写
    }
}
