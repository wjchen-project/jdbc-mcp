package com.jdbcmcp.tool;

import com.jdbcmcp.exporter.ExportTaskManager;
import com.jdbcmcp.exporter.ExportTaskSnapshot;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * get_export_task 工具：查询异步导出任务状态和已导出行数。
 */
@Slf4j
public class GetExportTaskTool {

    private static final String TOOL_NAME = "get_export_task";
    private static final String TOOL_DESCRIPTION =
            "Get an XLSX export task status, including exported row count, output path, timestamps and error message.";

    private final ExportTaskManager exportTaskManager;

    private GetExportTaskTool(ExportTaskManager exportTaskManager) {
        this.exportTaskManager = exportTaskManager;
    }

    public static McpServerFeatures.SyncToolSpecification create(ExportTaskManager exportTaskManager) {
        GetExportTaskTool tool = new GetExportTaskTool(exportTaskManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of("task_id", Map.of("type", "string", "description", "Export task ID returned by start_export_task")),
                List.of("task_id"), null, null, null);
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
            String taskId = (String) arguments.get("task_id");
            if (taskId == null || taskId.isBlank()) {
                return ResultFormatter.errorResult("Error: task_id is empty");
            }
            ExportTaskSnapshot snapshot = exportTaskManager.get(taskId);
            if (snapshot == null) {
                return ResultFormatter.errorResult("Error: export task not found: " + taskId);
            }
            return ResultFormatter.successResult(ExportTaskFormat.one(snapshot));
        } catch (Exception e) {
            log.error("Failed to get export task: {}", e.getMessage(), e);
            return ResultFormatter.errorResult("Error: " + e.getMessage());
        }
    }
}
