package com.jdbcmcp.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 单个数据源配置。
 * <p>
 * 所有数据源参数均从命令行参数解析，不再读取配置文件。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceConfig {

    public static final int DEFAULT_MAX_ROWS = 100;

    private String  driverClass;
    private String  url;
    private String  username;
    private String  password;
    @Builder.Default
    private boolean readOnly = true;
    @Builder.Default
    private int     maxRows  = DEFAULT_MAX_ROWS;
}
