package com.jdbcmcp.exporter;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.interceptor.QueryOnlyInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 异步 XLSX 导出任务管理器。
 * <p>
 * 每个任务在后台线程中独立获取 JDBC 连接、流式读取 ResultSet 并写入 XLSX 文件。
 * 任务状态保存在内存中，适配 MCP Server 的单进程运行模型。
 */
@Slf4j
public class ExportTaskManager implements AutoCloseable {

    private static final int DEFAULT_FETCH_SIZE = 1_000;

    private final DriverManager           connectionManager;
    private final ExecutorService         executorService;
    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();
    private final XlsxStreamingWriter     writer = new XlsxStreamingWriter();

    public ExportTaskManager(DriverManager connectionManager) {
        this.connectionManager = connectionManager;
        this.executorService = Executors.newCachedThreadPool(new ExportThreadFactory());
    }

    public ExportTaskSnapshot submit(String sql, String filePath) {
        validateSubmitArguments(sql, filePath);

        String taskId = UUID.randomUUID().toString();
        ExportTask task = new ExportTask(taskId, sql, normalizeOutputPath(filePath));
        tasks.put(taskId, task);

        Future<?> future = executorService.submit(() -> runTask(task));
        task.bindFuture(future);
        return task.snapshot();
    }

    public ExportTaskSnapshot get(String taskId) {
        ExportTask task = tasks.get(taskId);
        return task == null ? null : task.snapshot();
    }

    public List<ExportTaskSnapshot> list() {
        return tasks.values().stream()
                .map(ExportTask::snapshot)
                .sorted(Comparator.comparing(ExportTaskSnapshot::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public ExportTaskSnapshot cancel(String taskId) {
        ExportTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        task.requestCancel();
        return task.snapshot();
    }

    private void runTask(ExportTask task) {
        task.markStarted();
        Path outputPath = Path.of(task.getFilePath());
        Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + "." + task.getTaskId() + ".tmp");

        try {
            deleteIfExists(tempPath);
            try (Connection conn = connectionManager.getConnection();
                 Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                configureStatement(stmt);
                task.bindStatement(stmt);
                boolean hasResultSet = stmt.execute(task.getSql());
                if (!hasResultSet) {
                    throw new SQLException("Export SQL did not return a ResultSet");
                }
                try (ResultSet rs = stmt.getResultSet()) {
                    writer.write(rs, tempPath, () -> {
                        if (task.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                            throw new SQLException("Export task was cancelled");
                        }
                        task.incrementRows();
                    });
                }
            } finally {
                task.clearStatement();
            }

            if (task.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                task.markCancelled();
                deleteIfExists(tempPath);
                return;
            }

            Files.createDirectories(outputPath.toAbsolutePath().getParent());
            moveIntoPlace(tempPath, outputPath);
            task.markSucceeded();
            log.info("Export task {} succeeded. file={}, rows={}", task.getTaskId(), task.getFilePath(),
                    task.snapshot().getExportedRows());
        } catch (Exception e) {
            if (task.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                task.markCancelled();
                log.info("Export task {} cancelled. file={}, rows={}", task.getTaskId(), task.getFilePath(),
                        task.snapshot().getExportedRows());
            } else {
                task.markFailed(e);
                log.error("Export task {} failed: {}", task.getTaskId(), e.getMessage(), e);
            }
            try {
                deleteIfExists(tempPath);
            } catch (IOException cleanupError) {
                log.warn("Failed to delete temporary export file {}: {}", tempPath, cleanupError.getMessage(), cleanupError);
            }
        }
    }

    private void configureStatement(Statement stmt) throws SQLException {
        stmt.setFetchSize(DEFAULT_FETCH_SIZE);
        stmt.setQueryTimeout(0);
    }

    private void validateSubmitArguments(String sql, String filePath) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement is empty");
        }
        String rejection = QueryOnlyInterceptor.check(sql);
        if (rejection != null) {
            throw new IllegalArgumentException(rejection);
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Export file path is empty");
        }
        if (!filePath.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Export file path must end with .xlsx");
        }
    }

    private String normalizeOutputPath(String filePath) {
        return Path.of(filePath).toAbsolutePath().normalize().toString();
    }

    private void moveIntoPlace(Path tempPath, Path outputPath) throws IOException {
        try {
            Files.move(tempPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    private static class ExportThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "jdbc-mcp-export-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
