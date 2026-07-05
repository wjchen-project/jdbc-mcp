package com.jdbcmcp.tool;

import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.formatter.MarkdownTableBuilder;
import com.jdbcmcp.formatter.ResultFormatter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * get_table_schema 工具：获取指定表的列结构、类型等元数据。
 * 对应 JDBC {@link DatabaseMetaData#getColumns(String, String, String, String)}。
 * 支持通过 catalog、schema、table 参数精确筛选目标表。
 */
@Slf4j
public class GetTableSchemaTool extends AbstractMetaTool {

    private static final String TOOL_NAME        = "get_table_schema";
    private static final String TOOL_DESCRIPTION =
            "Get the column schema metadata for a specific table, including column name, data type, " +
                    "type name, column size, nullable, primary key, auto increment, and remarks. " +
                    "Supports optional catalog and schema parameters to narrow down the target table. " +
                    "Returns the result in Markdown format.";

    private GetTableSchemaTool(DriverManager connectionManager) {
        super(connectionManager);
    }

    public static McpServerFeatures.SyncToolSpecification create(DriverManager connectionManager) {
        GetTableSchemaTool tool = new GetTableSchemaTool(connectionManager);
        var schema = new McpSchema.JsonSchema("object",
                Map.of(
                        "catalog", Map.of("type", "string",
                                "description", "Optional catalog name to narrow the search. If not specified, matches all catalogs."),
                        "schema", Map.of("type", "string",
                                "description", "Optional schema name to narrow the search. If not specified, matches all schemas."),
                        "table", Map.of("type", "string",
                                "description", "Table name pattern (supports SQL LIKE wildcards). If not specified, matches all tables.")
                ),
                null, null, null, null);
        return buildSpecification(tool, TOOL_NAME, TOOL_DESCRIPTION, schema);
    }

    @Override
    protected CallToolResult doHandle(CallToolRequest request) throws Exception {
        Map<String, Object> arguments = request.arguments();
        String catalog = getOptionalString(arguments, "catalog");
        String schemaPattern = getOptionalString(arguments, "schema");
        String tablePattern = getOptionalString(arguments, "table");
        if (tablePattern == null) tablePattern = "%";

        try (Connection conn = getConnectionManager().getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();

            // 获取匹配的表，再用表实际所属的 catalog/schema 精确查询列信息
            try (ResultSet tables = dbMeta.getTables(catalog, schemaPattern, tablePattern, new String[]{"TABLE", "VIEW"})) {
                // 取第一个匹配表的列信息
                if (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableCatalog = tables.getString("TABLE_CAT");
                    String tableSchema = tables.getString("TABLE_SCHEM");

                    return ResultFormatter.successResult(formatTableSchema(dbMeta, tableCatalog, tableSchema, tableName));
                }
            }

            // 未匹配到任何表，返回空结果
            return ResultFormatter.successResult("No matching table found.");
        }
    }

    @Override
    protected void onError(Exception e) {
        log.error("Failed to get table schema: {}", e.getMessage(), e);
    }

    /**
     * 组装表字段结构，额外补充 JDBC getColumns 未直接提供的主键信息。
     */
    private String formatTableSchema(DatabaseMetaData dbMeta, String catalog, String schema, String tableName)
            throws SQLException {
        Set<String> primaryKeyColumns = getPrimaryKeyColumns(dbMeta, catalog, schema, tableName);

        MarkdownTableBuilder builder = new MarkdownTableBuilder()
                .header("COLUMN_NAME", "VARCHAR", 128)
                .header("DATA_TYPE", "INTEGER", 10)
                .header("TYPE_NAME", "VARCHAR", 128)
                .header("COLUMN_SIZE", "INTEGER", 10)
                .header("DECIMAL_DIGITS", "INTEGER", 10)
                .header("NULLABLE", "VARCHAR", 16)
                .header("PRIMARY_KEY", "VARCHAR", 8)
                .header("AUTO_INCREMENT", "VARCHAR", 16)
                .header("COLUMN_DEF", "VARCHAR", 256)
                .header("REMARKS", "VARCHAR", 512);

        try (ResultSet columns = dbMeta.getColumns(catalog, schema, tableName, "%")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                builder.row(
                        columnName,
                        getOptionalObject(columns, "DATA_TYPE"),
                        getOptionalObject(columns, "TYPE_NAME"),
                        getOptionalObject(columns, "COLUMN_SIZE"),
                        getOptionalObject(columns, "DECIMAL_DIGITS"),
                        getNullable(columns),
                        primaryKeyColumns.contains(normalizeIdentifier(columnName)) ? "YES" : "NO",
                        getAutoIncrement(columns),
                        getOptionalObject(columns, "COLUMN_DEF"),
                        getOptionalObject(columns, "REMARKS")
                );
            }
        }

        return builder.build();
    }

    private Set<String> getPrimaryKeyColumns(DatabaseMetaData dbMeta, String catalog, String schema, String tableName)
            throws SQLException {
        Set<String> primaryKeyColumns = new HashSet<>();
        try (ResultSet primaryKeys = dbMeta.getPrimaryKeys(catalog, schema, tableName)) {
            while (primaryKeys.next()) {
                primaryKeyColumns.add(normalizeIdentifier(primaryKeys.getString("COLUMN_NAME")));
            }
        }
        return primaryKeyColumns;
    }

    private static String getNullable(ResultSet columns) throws SQLException {
        String isNullable = getOptionalString(columns, "IS_NULLABLE");
        if (isNullable != null && !isNullable.isBlank()) {
            return isNullable;
        }

        Object nullable = getOptionalObject(columns, "NULLABLE");
        if (!(nullable instanceof Number)) {
            return "UNKNOWN";
        }

        return switch (((Number) nullable).intValue()) {
            case DatabaseMetaData.columnNoNulls -> "NO";
            case DatabaseMetaData.columnNullable -> "YES";
            default -> "UNKNOWN";
        };
    }

    private static String getAutoIncrement(ResultSet columns) throws SQLException {
        String autoIncrement = getOptionalString(columns, "IS_AUTOINCREMENT");
        return autoIncrement == null || autoIncrement.isBlank() ? "UNKNOWN" : autoIncrement;
    }

    private static Object getOptionalObject(ResultSet rs, String columnLabel) throws SQLException {
        try {
            Object value = rs.getObject(columnLabel);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            if (isMissingColumn(e)) {
                return null;
            }
            throw e;
        }
    }

    private static String getOptionalString(ResultSet rs, String columnLabel) throws SQLException {
        try {
            String value = rs.getString(columnLabel);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            if (isMissingColumn(e)) {
                return null;
            }
            throw e;
        }
    }

    private static boolean isMissingColumn(SQLException e) {
        String sqlState = e.getSQLState();
        if ("S0022".equals(sqlState) || "42S22".equals(sqlState)) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase(Locale.ROOT);
        return lowerMessage.contains("column") && (
                lowerMessage.contains("not found")
                        || lowerMessage.contains("not exist")
                        || lowerMessage.contains("unknown")
                        || lowerMessage.contains("invalid")
        );
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
    }
}
