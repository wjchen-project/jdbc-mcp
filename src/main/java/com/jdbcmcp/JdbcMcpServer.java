package com.jdbcmcp;

import com.jdbcmcp.config.ConfigManager;
import com.jdbcmcp.config.DatasourceConfig;
import com.jdbcmcp.connection.DriverClassLoader;
import com.jdbcmcp.connection.DriverManager;
import com.jdbcmcp.interceptor.SqlInterceptor;
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

        // 校验命令行参数：必须传入数据源名称
        if (args == null || args.length == 0) {
            log.error("Missing required argument: datasource name.");
            log.error("Usage: java -jar jdbc-mcp.jar <datasource_name>");
            System.exit(1);
        }

        String datasourceName = args[0];

        // 加载配置
        ConfigManager configManager;
        try {
            configManager = ConfigManager.load();
        } catch (Exception e) {
            log.error("Failed to load config: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        // 匹配数据源
        DatasourceConfig dsConfig = configManager.getDatasource(datasourceName);
        if (dsConfig == null) {
            log.error("Datasource '{}' not found in config.yml.", datasourceName);
            log.error("Available datasources: {}", configManager.getDatasourceNames());
            System.exit(1);
        }

        log.info("Using datasource: {}", datasourceName);

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
}
