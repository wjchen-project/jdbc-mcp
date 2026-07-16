package com.jdbcmcp;

import com.jdbcmcp.config.ArgOption;
import com.jdbcmcp.config.ArgParseResult;
import com.jdbcmcp.config.ArgParser;
import com.jdbcmcp.config.DatasourceConfig;
import com.jdbcmcp.connection.DriverClassLoader;
import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.interceptor.SqlInterceptor;
import com.jdbcmcp.exporter.ExportTaskManager;
import com.jdbcmcp.tool.CancelExportTaskTool;
import com.jdbcmcp.tool.GetExportTaskTool;
import com.jdbcmcp.tool.ListExportTasksTool;
import com.jdbcmcp.tool.StartExportTaskTool;
import com.jdbcmcp.tool.ExecuteQueryTool;
import com.jdbcmcp.tool.ExecuteUpdateTool;
import com.jdbcmcp.tool.GetTableSchemaTool;
import com.jdbcmcp.tool.ListCatalogTool;
import com.jdbcmcp.tool.ListSchemaTool;
import com.jdbcmcp.tool.ListTableTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

/**
 * JDBC-MCP Server 入口类。
 * 通过 stdio 实现 MCP 协议，向 Agent 提供数据库探查和操作能力。
 */
@Slf4j
public class JdbcMcpServer {

    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        // 设置日志目录为 JAR 同级目录，供 logback.xml 中的 ${app.home} 使用
        System.setProperty("app.home", getJarDir());
        ArgParser argParser = buildArgParser();

        if (isHelp(args)) {
            log.error(argParser.usage());
            System.exit(0);
        }

        // 校验命令行参数：必须传入完整 JDBC 连接参数
        if (args == null || args.length == 0) {
            log.error("Missing required datasource arguments.");
            log.error(argParser.usage());
            System.exit(1);
        }

        // 从命令行参数解析数据源配置，不再读取 config.yml
        DatasourceConfig dsConfig;
        try {
            ArgParseResult parsedArgs = argParser.parse(args);
            dsConfig = DatasourceConfig.builder()
                    .driverClass(parsedArgs.get("--driver-class"))
                    .url(parsedArgs.get("--url"))
                    .username(parsedArgs.get("--username"))
                    .password(parsedArgs.get("--password"))
                    .readOnly(!parsedArgs.contains("--danger-allow-write"))
                    .maxRows(Integer.parseInt(parsedArgs.getOrDefault("--max-rows",
                            String.valueOf(DatasourceConfig.DEFAULT_MAX_ROWS))))
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid datasource arguments: {}", e.getMessage());
            log.error(argParser.usage());
            System.exit(1);
            return;
        }

        log.info("Using JDBC driver: {}", dsConfig.getDriverClass());

        // 初始化驱动类加载器：扫描 driver/ 目录下的所有 JDBC 驱动 JAR
        DriverClassLoader driverClassLoader = new DriverClassLoader(getJarDir());

        // 初始化连接工厂
        DriverManager connectionManager;
        try {
            connectionManager = new DriverManager(dsConfig, driverClassLoader);
        } catch (Exception e) {
            log.error("Failed to initialize connection manager: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        // 初始化 SQL 拦截器
        SqlInterceptor interceptor = new SqlInterceptor(dsConfig.isReadOnly());

        ExportTaskManager exportTaskManager = new ExportTaskManager(connectionManager);

        // 构建 MCP Server
        try {
            StdioServerTransportProvider transportProvider =
                    new StdioServerTransportProvider(McpJsonMapper.getDefault());

            McpSyncServer server = McpServer.sync(transportProvider)
                    .serverInfo("jdbc-mcp", VERSION)
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .build())
                    .build();

            // 注册 list_catalogs 工具
            var listCatalogTool = ListCatalogTool.create(connectionManager);
            server.addTool(listCatalogTool);

            // 注册 list_schemas 工具
            var listSchemaTool = ListSchemaTool.create(connectionManager);
            server.addTool(listSchemaTool);

            // 注册 list_tables 工具
            var listTableTool = ListTableTool.create(connectionManager);
            server.addTool(listTableTool);

            // 注册 get_table_schema 工具
            var getTableSchemaTool = GetTableSchemaTool.create(connectionManager);
            server.addTool(getTableSchemaTool);

            // 注册 execute_query 工具
            var executeQueryTool = ExecuteQueryTool.create(connectionManager, dsConfig);
            server.addTool(executeQueryTool);

            // 注册 execute_update 工具
            var executeUpdateTool = ExecuteUpdateTool.create(connectionManager, interceptor);
            server.addTool(executeUpdateTool);

            server.addTool(StartExportTaskTool.create(exportTaskManager));
            server.addTool(GetExportTaskTool.create(exportTaskManager));
            server.addTool(ListExportTasksTool.create(exportTaskManager));
            server.addTool(CancelExportTaskTool.create(exportTaskManager));

            log.info("JDBC-MCP Server started successfully. Waiting for MCP requests...");

        } catch (Exception e) {
            log.error("Failed to start MCP server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * 获取应用根目录。
     * 优先使用系统属性 app.home（开发模式下由启动脚本注入），
     * 未设置时回退到 JAR 包所在目录。
     */
    private static String getJarDir() {
        String appHome = System.getProperty("app.home");
        if (appHome != null && !appHome.isBlank()) {
            return appHome;
        }

        try {
            String path = JdbcMcpServer.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            File file = new File(path);
            if (file.isFile()) {
                return file.getParent();
            }
            return file.getPath();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    private static boolean isHelp(String[] args) {
        return args != null && args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]));
    }

    private static ArgParser buildArgParser() {
        return ArgParser.builder()
                .usageHeader("""
                        Usage:
                          java -jar jdbc-mcp.jar \\
                            --driver-class <jdbc_driver_class> \\
                            --url <jdbc_url> \\
                            --username <username> \\
                            --password <password> \\
                            [--danger-allow-write] \\
                            [--max-rows <positive_integer>]
                        """)
                .register(ArgOption.builder()
                        .command("--driver-class")
                        .shortCommand("-d")
                        .description("JDBC driver full qualified class name.")
                        .skipParameterCount(1)
                        .required(true)
                        .validator(JdbcMcpServer::validateNotBlank)
                        .build())
                .register(ArgOption.builder()
                        .command("--url")
                        .description("JDBC connection URL.")
                        .skipParameterCount(1)
                        .required(true)
                        .validator(JdbcMcpServer::validateNotBlank)
                        .build())
                .register(ArgOption.builder()
                        .command("--username")
                        .shortCommand("-u")
                        .description("Database username.")
                        .skipParameterCount(1)
                        .required(true)
                        .build())
                .register(ArgOption.builder()
                        .command("--password")
                        .shortCommand("-p")
                        .description("Database password.")
                        .skipParameterCount(1)
                        .required(true)
                        .build())
                .register(ArgOption.builder()
                        .command("--danger-allow-write")
                        .description("Dangerously allow execute_update statements. Default: false.")
                        .skipParameterCount(0)
                        .build())
                .register(ArgOption.builder()
                        .command("--max-rows")
                        .shortCommand("-m")
                        .description("Maximum rows returned by execute_query. Default: 100.")
                        .skipParameterCount(1)
                        .validator(JdbcMcpServer::validatePositiveInteger)
                        .build())
                .build();
    }

    private static void validateNotBlank(ArgOption option, List<String> values) {
        String value = values.isEmpty() ? null : values.get(0);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(option.getCommand() + " cannot be blank.");
        }
    }

    private static void validatePositiveInteger(ArgOption option, List<String> values) {
        String value = values.isEmpty() ? null : values.get(0);
        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue <= 0) {
                throw new IllegalArgumentException(option.getCommand() + " must be a positive integer.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for " + option.getCommand() + ": " + value, e);
        }
    }
}
