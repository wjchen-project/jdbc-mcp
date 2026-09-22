package com.jdbcmcp.connection;

import com.jdbcmcp.config.DatasourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;

/**
 * 数据库连接管理器。
 * 基于 HikariCP 连接池复用连接，避免每次工具调用都建立新的物理连接。
 * JDBC 驱动通过 DriverClassLoader 从 driver/ 目录加载，并经
 * {@link DriverDataSourceAdapter} 适配为 DataSource 交给 HikariCP 建连。
 */
@Slf4j
@Getter
public class DriverManager implements AutoCloseable {

    /**
     * 池内最大连接数。MCP Server 单进程模型下并发量有限，5 个连接足够
     * 覆盖工具并发调用 + 后台导出任务的需求。
     */
    private static final int MAX_POOL_SIZE = 5;

    /**
     * 获取连接的最长等待时间（毫秒）。超时快速失败，
     * 避免连接耗尽时 Agent 的调用长时间挂起。
     */
    private static final long CONNECTION_TIMEOUT_MS = 10_000;

    private final DatasourceConfig config;
    private final Driver           driver;
    private final HikariDataSource dataSource;

    public DriverManager(DatasourceConfig config, DriverClassLoader driverClassLoader) throws Exception {
        this.config = config;

        // 通过 DriverClassLoader 加载驱动类并实例化
        Class<?> driverClass = driverClassLoader.loadClass(config.getDriverClass());
        this.driver = (Driver) driverClass.getDeclaredConstructor().newInstance();

        this.dataSource = buildDataSource();

        log.info("Driver loaded: {}", config.getDriverClass());
        log.info("HikariCP pool initialized: maxPoolSize={}, connectionTimeout={}ms",
                MAX_POOL_SIZE, CONNECTION_TIMEOUT_MS);
    }

    /**
     * 构建 Hikari 连接池。
     * 通过 setDataSource 注入适配器，使建连路径仍为 driver.connect(url, info)，
     * 兼容 DriverClassLoader 动态加载的驱动（不依赖 java.sql.DriverManager）。
     */
    private HikariDataSource buildDataSource() {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("jdbc-mcp-pool");
        hikari.setDataSource(new DriverDataSourceAdapter(
                driver, config.getUrl(), config.getUsername(), config.getPassword()));
        hikari.setMaximumPoolSize(MAX_POOL_SIZE);
        hikari.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        hikari.setAutoCommit(true);

        // 只读模式：HikariCP 在连接入池时自动调用 Connection.setReadOnly(true)
        if (config.isReadOnly()) {
            hikari.setReadOnly(true);
        }

        return new HikariDataSource(hikari);
    }

    /**
     * 从连接池获取连接。
     * 调用方 close() 连接时由 HikariCP 拦截并归还连接而非物理关闭。
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP pool closed.");
        }
    }
}
