package com.jdbcmcp.connection;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 驱动类加载器，扫描 {app.home}/driver/ 目录下的所有 JAR 文件并构建 URLClassLoader。
 * <p>
 * lib/ 目录存放项目运行时依赖（由 MANIFEST.MF Class-Path 管理），
 * driver/ 目录专门存放用户自行放入的 JDBC 驱动 JAR，由本类动态扫描加载。
 * 两个目录职责分离，互不干扰。
 */
@Slf4j
@Getter
public class DriverClassLoader {

    private final URLClassLoader classLoader;

    public DriverClassLoader(String appHome) {
        File driverDir = new File(appHome, "driver");
        List<URL> jarUrls = scanJarFiles(driverDir);

        this.classLoader = new URLClassLoader(
                jarUrls.toArray(URL[]::new),
                ClassLoader.getSystemClassLoader()
        );

        log.info("DriverClassLoader initialized: {} JARs loaded from {}", jarUrls.size(), driverDir.getAbsolutePath());
        for (URL url : jarUrls) {
            log.debug("  - {}", url.getFile());
        }
    }

    /**
     * 使用本类加载器加载指定类。
     * 加载顺序：先委托父加载器（系统类加载器 + MANIFEST.MF Class-Path），
     * 未找到时再搜索 driver/ 下的 JAR 文件。
     */
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, classLoader);
    }

    /**
     * 扫描目录下的所有 .jar 文件，返回 URL 列表。
     */
    private List<URL> scanJarFiles(File dir) {
        List<URL> urls = new ArrayList<>();
        if (!dir.isDirectory()) {
            log.warn("driver/ directory not found: {}", dir.getAbsolutePath());
            return urls;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null) {
            return urls;
        }

        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (Exception e) {
                log.warn("Failed to add JAR to classpath: {}", file.getAbsolutePath(), e);
            }
        }

        return urls;
    }
}
