package com.jdbcmcp.tool;

import com.jdbcmcp.exporter.ExportTaskManager;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * start_export_task 工具：启动异步 SQL 查询 XLSX 导出任务。
 */
@Slf4j
public class StartExportTaskTool {

    private static final String TOOL_NAME = "start_export_task";
    private static final String TOOL_DESCRIPTION =
            "Start an asynchronous XLSX export task. Parameters: sql (SELECT query) and file_path (.xlsx output path). " +
                    "The task runs in background for long-running exports. Use get_export_task to check status and exported row count.";

    private final ExportTaskManager exportTaskManager;

    private StartExportTaskTool(ExportTaskManager exportTaskManager) {
        this.exportTaskManager = exportTaskManager;
    }

    public static McpServerFeatures.SyncToolSpecification create(ExportTaskManager exportTaskManager) {
        StartExportTaskTool tool = new StartExportTaskTool(exportTaskManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of(
                        "sql", Map.of("type", "string", "description", "The SELECT SQL query to export"),
                        "file_path", Map.of("type", "string", "description", "Absolute or relative .xlsx output file path")
                ),
                List.of("sql", "file_path"), null, null, null);
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(schema)
                        .build())
                .callHandler((exchange, request) -> tool.handle(request.arguments()))
                .build();
    }

    private CallToolResult handle(Map<String, Object> arguments) {
        try {
            String sql = (String) arguments.get("sql");
            String filePath = (String) arguments.get("file_path");
            return ResultFormatter.successResult(ExportTaskFormat.one(exportTaskManager.submit(sql, filePath)));
        } catch (Exception e) {
            log.error("Failed to start export task: {}", e.getMessage(), e);
            return ResultFormatter.errorResult("Error: " + e.getMessage());
        }
    }
}
