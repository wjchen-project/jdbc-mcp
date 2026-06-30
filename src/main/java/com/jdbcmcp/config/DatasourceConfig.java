package com.jdbcmcp.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单个数据源配置
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceConfig {

    private String  name;
    private String  driverClass;
    private String  url;
    private String  username;
    private String  password;
    @Builder.Default
    private boolean readOnly = false;
    @Builder.Default
    private int     maxRows  = ConfigManager.getDefaultMaxRows();

    /**
     * 从 YAML 解析的 Map 中构建配置
     */
    public static DatasourceConfig fromMap(Map<String, Object> map) {
        Object maxRowsObj = map.get("max_rows");
        int maxRows = (maxRowsObj instanceof Number)
                ? ((Number) maxRowsObj).intValue()
                : ConfigManager.getDefaultMaxRows();

        return DatasourceConfig.builder()
                .name((String) map.get("name"))
                .driverClass((String) map.get("driver_class"))
                .url((String) map.get("url"))
                .username((String) map.get("username"))
                .password((String) map.get("password"))
                .readOnly(Boolean.TRUE.equals(map.get("read_only")))
                .maxRows(maxRows)
                .build();
    }
}
