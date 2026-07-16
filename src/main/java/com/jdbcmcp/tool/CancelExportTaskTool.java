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
 * cancel_export_task 工具：请求取消长时间运行的导出任务。
 */
@Slf4j
public class CancelExportTaskTool {

    private static final String TOOL_NAME = "cancel_export_task";
    private static final String TOOL_DESCRIPTION =
            "Request cancellation of a running XLSX export task. Cancellation is best-effort and progress can be checked with get_export_task.";

    private final ExportTaskManager exportTaskManager;

    private CancelExportTaskTool(ExportTaskManager exportTaskManager) {
        this.exportTaskManager = exportTaskManager;
    }

    public static McpServerFeatures.SyncToolSpecification create(ExportTaskManager exportTaskManager) {
        CancelExportTaskTool tool = new CancelExportTaskTool(exportTaskManager);
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
            ExportTaskSnapshot snapshot = exportTaskManager.cancel(taskId);
            if (snapshot == null) {
                return ResultFormatter.errorResult("Error: export task not found: " + taskId);
            }
            return ResultFormatter.successResult(ExportTaskFormat.one(snapshot));
        } catch (Exception e) {
            log.error("Failed to cancel export task: {}", e.getMessage(), e);
            return ResultFormatter.errorResult("Error: " + e.getMessage());
        }
    }
}
