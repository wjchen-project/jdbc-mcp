package com.jdbcmcp.exporter;

import lombok.Getter;

import java.time.Instant;
import java.sql.Statement;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个异步导出任务的运行时状态。
 */
class ExportTask {

    @Getter
    private final String                          taskId;
    @Getter
    private final String                          sql;
    @Getter
    private final String                          filePath;
    @Getter
    private final Instant                         createdAt;
    private final AtomicReference<ExportTaskStatus> status          = new AtomicReference<>(ExportTaskStatus.RUNNING);
    private final AtomicLong                      exportedRows    = new AtomicLong(0);
    private final AtomicBoolean                   cancelRequested = new AtomicBoolean(false);
    private volatile Instant                      startedAt;
    private volatile Instant                      finishedAt;
    private volatile String                       errorMessage;
    private volatile Future<?>                    future;
    private volatile Statement                    activeStatement;

    ExportTask(String taskId, String sql, String filePath) {
        this.taskId = taskId;
        this.sql = sql;
        this.filePath = filePath;
        this.createdAt = Instant.now();
    }

    void bindFuture(Future<?> future) {
        this.future = future;
    }

    void bindStatement(Statement statement) {
        this.activeStatement = statement;
    }

    void clearStatement() {
        this.activeStatement = null;
    }

    void markStarted() {
        this.startedAt = Instant.now();
    }

    void incrementRows() {
        exportedRows.incrementAndGet();
    }

    boolean isCancelRequested() {
        return cancelRequested.get();
    }

    void requestCancel() {
        cancelRequested.set(true);
        Statement statement = activeStatement;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception ignored) {
                // 取消是尽力而为，部分 JDBC 驱动可能不支持 Statement.cancel()。
            }
        }
        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
    }

    void markSucceeded() {
        status.set(ExportTaskStatus.SUCCEEDED);
        finishedAt = Instant.now();
    }

    void markCancelled() {
        status.set(ExportTaskStatus.CANCELLED);
        finishedAt = Instant.now();
    }

    void markFailed(Exception e) {
        status.set(ExportTaskStatus.FAILED);
        errorMessage = e.getMessage();
        finishedAt = Instant.now();
    }

    ExportTaskSnapshot snapshot() {
        return ExportTaskSnapshot.builder()
                .taskId(taskId)
                .status(status.get())
                .sql(sql)
                .filePath(filePath)
                .exportedRows(exportedRows.get())
                .createdAt(createdAt)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .errorMessage(errorMessage)
                .cancelRequested(cancelRequested.get())
                .build();
    }
}
