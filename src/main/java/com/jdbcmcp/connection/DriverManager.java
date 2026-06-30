package com.jdbcmcp.connection;

import com.jdbcmcp.config.DatasourceConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据库连接管理器。
 * 通过反射实例化 Driver，绕过 java.sql.DriverManager 机制直接建立 Connection。
 * JDBC 驱动通过 DriverClassLoader 从 driver/ 目录加载。
 */
@Slf4j
@Getter
public class DriverManager {

    private final DatasourceConfig config;
    private final Driver           driver;

    public DriverManager(DatasourceConfig config, DriverClassLoader driverClassLoader) throws Exception {
        this.config = config;

        // 通过 DriverClassLoader 加载驱动类并实例化
        Class<?> driverClass = driverClassLoader.loadClass(config.getDriverClass());
        this.driver = (Driver) driverClass.getDeclaredConstructor().newInstance();

        log.info("Driver loaded: {}", config.getDriverClass());
    }

    /**
     * 获取数据库连接。
     * 严禁使用 java.sql.DriverManager.getConnection()，
     * 必须通过 driver.connect() 直接建立连接。
     */
    public Connection getConnection() throws SQLException {
        Properties info = new Properties();
        info.setProperty("user", config.getUsername());
        info.setProperty("password", config.getPassword());

        Connection conn = driver.connect(config.getUrl(), info);
        if (conn == null) {
            throw new SQLException("Driver returned null connection for URL: " + config.getUrl());
        }

        // 只读模式：连接层保护
        if (config.isReadOnly()) {
            conn.setReadOnly(true);
        }

        return conn;
    }
}
