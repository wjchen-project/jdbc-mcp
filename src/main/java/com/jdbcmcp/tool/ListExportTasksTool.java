package com.jdbcmcp.tool;

import com.jdbcmcp.exporter.ExportTaskManager;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * list_export_tasks 工具：列出当前进程内的导出任务。
 */
@Slf4j
public class ListExportTasksTool {

    private static final String TOOL_NAME = "list_export_tasks";
    private static final String TOOL_DESCRIPTION =
            "List XLSX export tasks in the current server process, including status and exported row counts.";

    private final ExportTaskManager exportTaskManager;

    private ListExportTasksTool(ExportTaskManager exportTaskManager) {
        this.exportTaskManager = exportTaskManager;
    }

    public static McpServerFeatures.SyncToolSpecification create(ExportTaskManager exportTaskManager) {
        ListExportTasksTool tool = new ListExportTasksTool(exportTaskManager);
        var schema = new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(schema)
                        .build())
                .callHandler((exchange, request) -> tool.handle())
                .build();
    }

    private CallToolResult handle() {
        try {
            return ResultFormatter.successResult(ExportTaskFormat.list(exportTaskManager.list()));
        } catch (Exception e) {
            log.error("Failed to list export tasks: {}", e.getMessage(), e);
            return ResultFormatter.errorResult("Error: " + e.getMessage());
        }
    }
}
