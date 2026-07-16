package com.jdbcmcp.exporter;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 导出任务的只读快照，用于 MCP 工具返回任务进度。
 */
@Getter
@Builder
public class ExportTaskSnapshot {

    private final String           taskId;
    private final ExportTaskStatus status;
    private final String           sql;
    private final String           filePath;
    private final long             exportedRows;
    private final Instant          createdAt;
    private final Instant          startedAt;
    private final Instant          finishedAt;
    private final String           errorMessage;
    private final boolean          cancelRequested;
}
