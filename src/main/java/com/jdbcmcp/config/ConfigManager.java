package com.jdbcmcp.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置管理器：从 JAR 同级目录下的 config.yml 加载配置
 */
@Slf4j
@Getter
public class ConfigManager {

    private static final String CONFIG_FILE      = "config.yml";
    private static final int    DEFAULT_MAX_ROWS = 100;

    private final List<DatasourceConfig> datasources;

    public ConfigManager(List<DatasourceConfig> datasources) {
        this.datasources = datasources;
    }

    /**
     * 从 JAR 同级目录下的 config.yml 加载配置
     */
    public static ConfigManager load() throws IOException {
        String baseDir = getJarDir();
        Path configPath = Paths.get(baseDir, CONFIG_FILE);

        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configPath);
        }

        log.info("Loading config from: {}", configPath.toAbsolutePath());

        Yaml yaml = new Yaml();
        Map<String, Object> root;

        try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
            root = yaml.load(fis);
        }

        if (root == null || !root.containsKey("datasources")) {
            throw new IOException("Invalid config: 'datasources' section is missing");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dsList = (List<Map<String, Object>>) root.get("datasources");

        List<DatasourceConfig> datasources = new ArrayList<>();
        for (Map<String, Object> dsMap : dsList) {
            datasources.add(DatasourceConfig.fromMap(dsMap));
        }

        return new ConfigManager(datasources);
    }

    /**
     * 获取当前 JAR 包所在目录
     */
    private static String getJarDir() {
        try {
            String path = ConfigManager.class
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

    public static int getDefaultMaxRows() {
        return DEFAULT_MAX_ROWS;
    }

    /**
     * 按名称查找数据源配置
     */
    public DatasourceConfig getDatasource(String name) {
        return datasources.stream()
                .filter(ds -> ds.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有数据源名称列表
     */
    public String getDatasourceNames() {
        return datasources.stream()
                .map(DatasourceConfig::getName)
                .collect(Collectors.joining(", "));
    }
}
