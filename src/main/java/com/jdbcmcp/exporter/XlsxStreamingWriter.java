package com.jdbcmcp.exporter;

import com.jdbcmcp.formatter.ResultFormatter;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 低内存 XLSX 流式写入器。
 * <p>
 * 仅使用 JDK 标准库生成 Office Open XML 包，避免为通用 JDBC 工具引入重量级 Excel 依赖。
 * 写入过程按行流式消费 ResultSet，适合长时间、大结果集导出。
 */
class XlsxStreamingWriter {

    private static final int EXCEL_MAX_ROWS_PER_SHEET = 1_048_576;
    private static final int HEADER_ROWS_PER_SHEET    = 1;
    private static final int DATA_ROWS_PER_SHEET      = EXCEL_MAX_ROWS_PER_SHEET - HEADER_ROWS_PER_SHEET;
    private static final int MAX_CELL_TEXT_LENGTH     = 32_767;

    interface RowProgressListener {
        void onRowExported() throws SQLException;
    }

    void write(ResultSet rs, Path outputPath, RowProgressListener progressListener) throws SQLException, IOException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Files.createDirectories(outputPath.toAbsolutePath().getParent());

        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(outputPath));
             ZipOutputStream zipOut = new ZipOutputStream(fileOut, StandardCharsets.UTF_8)) {
            List<String> sheetNames = new ArrayList<>();
            writeFixedPackageParts(zipOut);

            int sheetIndex = 1;
            int rowsInCurrentSheet = 0;
            SheetWriter sheetWriter = null;

            try {
                while (rs.next()) {
                    if (sheetWriter == null || rowsInCurrentSheet >= DATA_ROWS_PER_SHEET) {
                        if (sheetWriter != null) {
                            sheetWriter.close();
                        }
                        String sheetName = "Data" + sheetIndex;
                        sheetNames.add(sheetName);
                        sheetWriter = openSheet(zipOut, sheetIndex, meta, columnCount);
                        sheetIndex++;
                        rowsInCurrentSheet = 0;
                    }

                    sheetWriter.writeDataRow(rs, meta, columnCount);
                    rowsInCurrentSheet++;
                    progressListener.onRowExported();
                }

                if (sheetWriter == null) {
                    String sheetName = "Data1";
                    sheetNames.add(sheetName);
                    sheetWriter = openSheet(zipOut, 1, meta, columnCount);
                }
            } finally {
                if (sheetWriter != null) {
                    sheetWriter.close();
                }
            }

            writeWorkbook(zipOut, sheetNames);
            writeWorkbookRels(zipOut, sheetNames.size());
            writeContentTypes(zipOut, sheetNames.size());
        }
    }

    private SheetWriter openSheet(ZipOutputStream zipOut,
                                  int sheetIndex,
                                  ResultSetMetaData meta,
                                  int columnCount) throws IOException, SQLException {
        zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheetIndex + ".xml"));
        SheetWriter writer = new SheetWriter(zipOut);
        writer.open();
        writer.writeHeader(meta, columnCount);
        return writer;
    }

    private void writeFixedPackageParts(ZipOutputStream zipOut) throws IOException {
        putEntry(zipOut, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
                </Relationships>
                """);

        putEntry(zipOut, "docProps/app.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
                  <Application>jdbc-mcp</Application>
                </Properties>
                """);

        String now = DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
        putEntry(zipOut, "docProps/core.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dc:creator>jdbc-mcp</dc:creator>
                  <cp:lastModifiedBy>jdbc-mcp</cp:lastModifiedBy>
                  <dcterms:created xsi:type="dcterms:W3CDTF">%s</dcterms:created>
                  <dcterms:modified xsi:type="dcterms:W3CDTF">%s</dcterms:modified>
                </cp:coreProperties>
                """.formatted(now, now));
    }

    private void writeWorkbook(ZipOutputStream zipOut, List<String> sheetNames) throws IOException {
        StringBuilder sheets = new StringBuilder();
        for (int i = 0; i < sheetNames.size(); i++) {
            int sheetId = i + 1;
            sheets.append("    <sheet name=\"")
                    .append(escapeXmlAttribute(sheetNames.get(i)))
                    .append("\" sheetId=\"")
                    .append(sheetId)
                    .append("\" r:id=\"rId")
                    .append(sheetId)
                    .append("\"/>\n");
        }
        putEntry(zipOut, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                %s  </sheets>
                </workbook>
                """.formatted(sheets));
    }

    private void writeWorkbookRels(ZipOutputStream zipOut, int sheetCount) throws IOException {
        StringBuilder relationships = new StringBuilder();
        for (int i = 1; i <= sheetCount; i++) {
            relationships.append("  <Relationship Id=\"rId")
                    .append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i)
                    .append(".xml\"/>\n");
        }
        putEntry(zipOut, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                %s</Relationships>
                """.formatted(relationships));
    }

    private void writeContentTypes(ZipOutputStream zipOut, int sheetCount) throws IOException {
        StringBuilder overrides = new StringBuilder();
        for (int i = 1; i <= sheetCount; i++) {
            overrides.append("  <Override PartName=\"/xl/worksheets/sheet")
                    .append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        putEntry(zipOut, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                %s</Types>
                """.formatted(overrides));
    }

    private void putEntry(ZipOutputStream zipOut, String name, String content) throws IOException {
        zipOut.putNextEntry(new ZipEntry(name));
        zipOut.write(content.getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
    }

    private static class SheetWriter {
        private final ZipOutputStream zipOut;
        private int nextRowNumber = 1;

        private SheetWriter(ZipOutputStream zipOut) {
            this.zipOut = zipOut;
        }

        void open() throws IOException {
            write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            write("<sheetData>");
        }

        void writeHeader(ResultSetMetaData meta, int columnCount) throws SQLException, IOException {
            write("<row r=\"" + nextRowNumber + "\">");
            for (int i = 1; i <= columnCount; i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isBlank()) {
                    label = meta.getColumnName(i);
                }
                writeInlineStringCell(i, nextRowNumber, label);
            }
            write("</row>");
            nextRowNumber++;
        }

        void writeDataRow(ResultSet rs, ResultSetMetaData meta, int columnCount) throws SQLException, IOException {
            write("<row r=\"" + nextRowNumber + "\">");
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                if (rs.wasNull()) {
                    continue;
                }
                writeCell(i, nextRowNumber, meta.getColumnType(i), value);
            }
            write("</row>");
            nextRowNumber++;
        }

        void close() throws IOException {
            write("</sheetData></worksheet>");
            zipOut.closeEntry();
        }

        private void writeCell(int columnIndex, int rowNumber, int sqlType, Object value) throws IOException {
            if (isNumericType(sqlType) && value instanceof Number number && isFinite(number)) {
                write("<c r=\"" + cellRef(columnIndex, rowNumber) + "\"><v>" + numericText(number) + "</v></c>");
                return;
            }
            if (sqlType == Types.BOOLEAN || sqlType == Types.BIT) {
                if (value instanceof Boolean bool) {
                    write("<c r=\"" + cellRef(columnIndex, rowNumber) + "\" t=\"b\"><v>" + (bool ? "1" : "0") + "</v></c>");
                    return;
                }
            }
            writeInlineStringCell(columnIndex, rowNumber, toCellText(value));
        }

        private void writeInlineStringCell(int columnIndex, int rowNumber, String value) throws IOException {
            write("<c r=\"" + cellRef(columnIndex, rowNumber) + "\" t=\"inlineStr\"><is><t>" +
                    escapeXmlText(truncateCellText(value)) + "</t></is></c>");
        }

        private void write(String text) throws IOException {
            zipOut.write(text.getBytes(StandardCharsets.UTF_8));
        }
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

    private static String numericText(Number number) {
        return number.toString().toUpperCase(Locale.ROOT);
    }

    private static String toCellText(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof OffsetTime time) {
            return time.toString();
        }
        if (value instanceof byte[] bytes) {
            return "<binary " + bytes.length + " bytes>";
        }
        return String.valueOf(value);
    }

    private static String truncateCellText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = ResultFormatter.escapeMarkdown(value)
                .replace("\\|", "|");
        if (normalized.length() <= MAX_CELL_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CELL_TEXT_LENGTH);
    }

    private static String cellRef(int columnIndex, int rowNumber) {
        StringBuilder columnName = new StringBuilder();
        int current = columnIndex;
        while (current > 0) {
            current--;
            columnName.insert(0, (char) ('A' + (current % 26)));
            current /= 26;
        }
        return columnName + String.valueOf(rowNumber);
    }

    private static String escapeXmlAttribute(String value) {
        return escapeXmlText(value).replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeXmlText(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '\r', '\n', '\t' -> escaped.append(ch);
                default -> {
                    if (isValidXmlChar(ch)) {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static boolean isValidXmlChar(char ch) {
        return ch == 0x9 || ch == 0xA || ch == 0xD ||
                (ch >= 0x20 && ch <= 0xD7FF) ||
                (ch >= 0xE000 && ch <= 0xFFFD);
    }
}
