package com.jdbcmcp.tool;

import com.jdbcmcp.exporter.ExportTaskSnapshot;
import com.jdbcmcp.formatter.ResultFormatter;

import java.time.Instant;
import java.util.List;

/**
 * 导出任务 MCP 响应格式化工具。
 */
class ExportTaskFormat {

    private ExportTaskFormat() {
    }

    static String one(ExportTaskSnapshot task) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Export Task\n\n");
        appendLine(builder, "Task ID", task.getTaskId());
        appendLine(builder, "Status", task.getStatus().name());
        appendLine(builder, "Exported Rows", String.valueOf(task.getExportedRows()));
        appendLine(builder, "File Path", task.getFilePath());
        appendLine(builder, "Created At", formatInstant(task.getCreatedAt()));
        appendLine(builder, "Started At", formatInstant(task.getStartedAt()));
        appendLine(builder, "Finished At", formatInstant(task.getFinishedAt()));
        appendLine(builder, "Cancel Requested", String.valueOf(task.isCancelRequested()));
        if (task.getErrorMessage() != null && !task.getErrorMessage().isBlank()) {
            appendLine(builder, "Error", task.getErrorMessage());
        }
        return builder.toString();
    }

    static String list(List<ExportTaskSnapshot> tasks) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Export Tasks\n\n");
        builder.append("| Task ID | Status | Exported Rows | File Path | Created At | Finished At | Error |\n");
        builder.append("| --- | --- | ---: | --- | --- | --- | --- |\n");
        for (ExportTaskSnapshot task : tasks) {
            builder.append("| ")
                    .append(escape(task.getTaskId())).append(" | ")
                    .append(escape(task.getStatus().name())).append(" | ")
                    .append(task.getExportedRows()).append(" | ")
                    .append(escape(task.getFilePath())).append(" | ")
                    .append(escape(formatInstant(task.getCreatedAt()))).append(" | ")
                    .append(escape(formatInstant(task.getFinishedAt()))).append(" | ")
                    .append(escape(task.getErrorMessage())).append(" |\n");
        }
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String key, String value) {
        builder.append("- **")
                .append(key)
                .append(":** ")
                .append(value == null || value.isBlank() ? "-" : ResultFormatter.escapeMarkdown(value))
                .append("\n");
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String escape(String value) {
        return value == null || value.isBlank() ? "-" : ResultFormatter.escapeMarkdown(value);
    }
}
