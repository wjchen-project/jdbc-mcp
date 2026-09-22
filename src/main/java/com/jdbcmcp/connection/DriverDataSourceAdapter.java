package com.jdbcmcp.connection;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * 将 {@link Driver} 实例适配为 {@link DataSource}。
 * <p>
 * JDBC 驱动由 {@link DriverClassLoader}（系统类加载器的子加载器）动态加载，
 * HikariCP 默认的 driverClassName 建连路径走 java.sql.DriverManager，
 * 因 caller classloader 可见性检查无法感知子加载器中的驱动，会抛 "No suitable driver"。
 * 本适配器直接委托 {@link Driver#connect(String, Properties)} 建连，
 * 交给 HikariCP（{@code HikariConfig#setDataSource}）后即可安全入池。
 */
public class DriverDataSourceAdapter implements DataSource {

    private final Driver     driver;
    private final String     url;
    private final Properties info;

    public DriverDataSourceAdapter(Driver driver, String url, String username, String password) {
        this.driver = driver;
        this.url = url;
        this.info = new Properties();
        if (username != null) {
            info.setProperty("user", username);
        }
        if (password != null) {
            info.setProperty("password", password);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = driver.connect(url, info);
        if (conn == null) {
            throw new SQLException("Driver returned null connection for URL: " + url);
        }
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException("Per-call credentials are not supported; credentials are fixed at startup.");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // no-op
    }

    @Override
    public void setLoginTimeout(int seconds) {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger is not supported by this adapter.");
    }
}
