package com.jdbcmcp.exporter;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.metadata.WriteSheet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Apache Fesod Sheet 的 XLSX 流式写入器。
 * <p>
 * 写入过程按批次消费 {@link ResultSet}，避免一次性把完整查询结果加载到内存；
 * 当单个 sheet 达到 Excel 行数上限时，会自动切换到下一个 sheet。
 */
class XlsxStreamingWriter {

    private static final int EXCEL_MAX_ROWS_PER_SHEET = 1_048_576;
    private static final int HEADER_ROWS_PER_SHEET    = 1;
    private static final int DATA_ROWS_PER_SHEET      = EXCEL_MAX_ROWS_PER_SHEET - HEADER_ROWS_PER_SHEET;
    private static final int WRITE_BATCH_SIZE         = 1_000;
    private static final int MAX_CELL_TEXT_LENGTH     = 32_767;

    interface RowProgressListener {
        void onRowExported() throws SQLException;
    }

    void write(ResultSet rs, Path outputPath, RowProgressListener progressListener) throws SQLException, IOException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<List<String>> head = buildHead(meta, columnCount);

        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        try (ExcelWriter excelWriter = FesodSheet.write(outputPath.toFile())
                .excelType(ExcelTypeEnum.XLSX)
                .build()) {
            WriteSheet currentSheet = buildSheet(0, head);
            List<List<Object>> batch = new ArrayList<>(WRITE_BATCH_SIZE);
            int rowsInCurrentSheet = 0;
            boolean wroteAnyBatch = false;

            while (rs.next()) {
                if (rowsInCurrentSheet >= DATA_ROWS_PER_SHEET) {
                    flush(excelWriter, currentSheet, batch, progressListener);
                    wroteAnyBatch = true;
                    currentSheet = buildSheet(currentSheet.getSheetNo() + 1, head);
                    rowsInCurrentSheet = 0;
                }

                batch.add(readRow(rs, meta, columnCount));
                rowsInCurrentSheet++;

                if (batch.size() >= WRITE_BATCH_SIZE) {
                    flush(excelWriter, currentSheet, batch, progressListener);
                    wroteAnyBatch = true;
                }
            }

            if (!batch.isEmpty()) {
                flush(excelWriter, currentSheet, batch, progressListener);
                wroteAnyBatch = true;
            }

            // 空结果集也输出一个仅包含表头的工作表，便于用户确认导出结构。
            if (!wroteAnyBatch) {
                excelWriter.write(List.of(), currentSheet);
            }
        }
    }

    private WriteSheet buildSheet(int sheetNo, List<List<String>> head) {
        return FesodSheet.writerSheet(sheetNo, "Data" + (sheetNo + 1))
                .head(head)
                .build();
    }

    private void flush(ExcelWriter excelWriter,
                       WriteSheet writeSheet,
                       List<List<Object>> batch,
                       RowProgressListener progressListener) throws SQLException {
        if (batch.isEmpty()) {
            return;
        }

        excelWriter.write(batch, writeSheet);
        for (int i = 0; i < batch.size(); i++) {
            progressListener.onRowExported();
        }
        batch.clear();
    }

    private List<List<String>> buildHead(ResultSetMetaData meta, int columnCount) throws SQLException {
        List<List<String>> head = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            head.add(List.of(label == null ? "COLUMN_" + i : label));
        }
        return head;
    }

    private List<Object> readRow(ResultSet rs, ResultSetMetaData meta, int columnCount) throws SQLException {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            Object value = rs.getObject(i);
            row.add(rs.wasNull() ? null : normalizeCellValue(meta.getColumnType(i), value));
        }
        return row;
    }

    private Object normalizeCellValue(int sqlType, Object value) {
        if (value == null) {
            return null;
        }

        if (isNumericType(sqlType) && value instanceof Number number) {
            return isFinite(number) ? value : toCellText(value);
        }

        if ((sqlType == Types.BOOLEAN || sqlType == Types.BIT) && value instanceof Boolean) {
            return value;
        }

        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date
                || value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof Boolean) {
            return value;
        }

        return toCellText(value);
    }

    private static boolean isNumericType(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.FLOAT, Types.REAL, Types.DOUBLE,
                    Types.NUMERIC, Types.DECIMAL -> true;
            default -> false;
        };
    }

    private static boolean isFinite(Number number) {
        if (number instanceof Double d) {
            return Double.isFinite(d);
        }
        if (number instanceof Float f) {
            return Float.isFinite(f);
        }
        return true;
    }

    private static String toCellText(Object value) {
        String text;
        if (value instanceof java.sql.Date date) {
            text = date.toLocalDate().toString();
        } else if (value instanceof java.sql.Time time) {
            text = time.toLocalTime().toString();
        } else if (value instanceof java.sql.Timestamp timestamp) {
            text = timestamp.toLocalDateTime().toString();
        } else if (value instanceof LocalTime time) {
            text = time.toString();
        } else if (value instanceof OffsetDateTime dateTime) {
            text = dateTime.toString();
        } else if (value instanceof OffsetTime time) {
            text = time.toString();
        } else if (value instanceof byte[] bytes) {
            text = "<binary " + bytes.length + " bytes>";
        } else {
            text = String.valueOf(value);
        }
        return truncateCellText(sanitizeXmlText(text));
    }

    private static String truncateCellText(String value) {
        if (value == null || value.length() <= MAX_CELL_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CELL_TEXT_LENGTH);
    }

    private static String sanitizeXmlText(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isValidXmlChar(ch)) {
                sanitized.append(ch);
            }
        }
        return sanitized.toString();
    }

    private static boolean isValidXmlChar(char ch) {
        return ch == 0x9 || ch == 0xA || ch == 0xD ||
                (ch >= 0x20 && ch <= 0xD7FF) ||
                (ch >= 0xE000 && ch <= 0xFFFD);
    }
}
