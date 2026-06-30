package com.jdbcmcp.config;

import java.util.Map;

/**
 * 单个数据源配置
 */
public class DatasourceConfig {

    private final String name;
    private final String driverClass;
    private final String url;
    private final String username;
    private final String password;
    private final boolean readOnly;
    private final int maxRows;

    private DatasourceConfig(Builder builder) {
        this.name = builder.name;
        this.driverClass = builder.driverClass;
        this.url = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.readOnly = builder.readOnly;
        this.maxRows = builder.maxRows;
    }

    /**
     * 从 YAML 解析的 Map 中构建配置
     */
    @SuppressWarnings("unchecked")
    public static DatasourceConfig fromMap(Map<String, Object> map) {
        Builder builder = new Builder();
        builder.name = (String) map.get("name");
        builder.driverClass = (String) map.get("driver_class");
        builder.url = (String) map.get("url");
        builder.username = (String) map.get("username");
        builder.password = (String) map.get("password");
        builder.readOnly = Boolean.TRUE.equals(map.get("read_only"));

        Object maxRowsObj = map.get("max_rows");
        builder.maxRows = (maxRowsObj instanceof Number)
                ? ((Number) maxRowsObj).intValue()
                : ConfigManager.getDefaultMaxRows();

        return builder.build();
    }

    // Getters
    public String getName() { return name; }
    public String getDriverClass() { return driverClass; }
    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isReadOnly() { return readOnly; }
    public int getMaxRows() { return maxRows; }

    public static class Builder {
        private String name;
        private String driverClass;
        private String url;
        private String username;
        private String password;
        private boolean readOnly = false;
        private int maxRows = ConfigManager.getDefaultMaxRows();

        public Builder name(String name) { this.name = name; return this; }
        public Builder driverClass(String driverClass) { this.driverClass = driverClass; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder readOnly(boolean readOnly) { this.readOnly = readOnly; return this; }
        public Builder maxRows(int maxRows) { this.maxRows = maxRows; return this; }

        public DatasourceConfig build() {
            return new DatasourceConfig(this);
        }
    }
}
