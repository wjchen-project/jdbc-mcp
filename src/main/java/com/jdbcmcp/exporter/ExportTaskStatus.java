package com.jdbcmcp.exporter;

/**
 * 异步导出任务状态。
 */
public enum ExportTaskStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
