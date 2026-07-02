# JDBC-MCP 项目参考文档

本文档描述项目代码的实际结构与实现细节，供开发参考。

---

## 1. 项目概述

JDBC-MCP 是一个基于 Model Context Protocol (MCP) 的通用 JDBC 数据库工具，通过 `stdio` 与大模型（Agent）通信，提供数据库探查与操作能力。

**技术栈**: Java 17 / Maven / MCP SDK 0.17.2 / SnakeYAML 2.2 / SLF4J 2.0.16 + Logback 1.5.16 / Lombok 1.18.38

---

## 2. 代码结构

```
src/main/java/com/jdbcmcp/
├── JdbcMcpServer.java                  # 入口类，MCP Server 构建 & 工具注册
├── config/
│   ├── ConfigManager.java              # config.yml 加载与数据源匹配
│   └── DatasourceConfig.java           # 单数据源配置 POJO
├── connection/
│   ├── DriverClassLoader.java          # 驱动类加载器（扫描 driver/ 下所有 JAR）
│   └── DriverManager.java              # JDBC 连接管理器（反射加载 Driver）
├── formatter/
│   ├── MarkdownTableBuilder.java       # Markdown 表格流式构建器（# Schema + # Data）
│   └── ResultFormatter.java            # ResultSet → Markdown 格式化 & MCP 响应构建
├── interceptor/
│   ├── QueryOnlyInterceptor.java       # 无条件只读拦截器（execute_query 使用）
│   └── SqlInterceptor.java             # 条件性只读拦截器（execute_update 使用）
└── tool/
    ├── AbstractMetaTool.java            # 工具抽象基类
    ├── ListCatalogTool.java             # list_catalogs
    ├── ListSchemaTool.java              # list_schemas
    ├── ListTableTool.java               # list_tables
    ├── GetTableSchemaTool.java          # get_table_schema
    ├── ExecuteQueryTool.java            # execute_query
    └── ExecuteUpdateTool.java           # execute_update
```

---

## 3. MCP 工具一览

| 工具名 | 类 | 参数 | 说明 |
|--------|----|------|------|
| `list_catalogs` | ListCatalogTool | 无 | 列出所有 catalog |
| `list_schemas` | ListSchemaTool | `catalog?` (optional) | 列出 schema，可按 catalog 过滤 |
| `list_tables` | ListTableTool | `catalog?`, `schema?`, `table_pattern?` (默认 `%`) | 列出表和视图 |
| `get_table_schema` | GetTableSchemaTool | `catalog?`, `schema?`, `table?` (默认 `%`) | 获取指定表的列元数据 |
| `execute_query` | ExecuteQueryTool | `sql` (required) | 执行 SELECT 查询，返回 Markdown 格式 |
| `execute_update` | ExecuteUpdateTool | `sql` (required) | 执行修改 SQL，返回受影响行数 |

---

## 4. 核心模块实现

### 4.1 入口与启动 (`JdbcMcpServer`)

启动流程：
1. 设置 `app.home` 系统属性（JAR 所在目录）→ 供 logback 使用
2. 校验 `args[0]` 为数据源名称 → 缺失或不匹配则 `System.exit(1)`
3. `ConfigManager.load()` 加载配置
4. `new DriverClassLoader(appHome)` 初始化驱动类加载器（扫描 `driver/` 下所有 JAR）
5. `new DriverManager(dsConfig, driverClassLoader)` 初始化连接管理器
6. `new SqlInterceptor(dsConfig.isReadOnly())` 初始化拦截器
7. 构建 MCP Sync Server（Stdio 传输）→ 依次注册 6 个工具

### 4.2 配置管理 (`ConfigManager` + `DatasourceConfig`)

- **ConfigManager**: 从 JAR 同级目录的 `config.yml` 加载，SnakeYAML 解析，通过 `DatasourceConfig.fromMap()` 构建配置对象
- **DatasourceConfig** 字段:

| 字段 | 类型 | 默认值 | YAML 键 |
|------|------|--------|---------|
| name | String | — | `name` |
| driverClass | String | — | `driver_class` |
| url | String | — | `url` |
| username | String | — | `username` |
| password | String | — | `password` |
| readOnly | boolean | `false` | `read_only` |
| maxRows | int | `100` | `max_rows` |

### 4.3 类加载与连接管理 (`DriverClassLoader` + `DriverManager`)

- **DriverClassLoader**: 启动时扫描 `{app.home}/driver/` 下所有 `.jar` 文件，构建 `URLClassLoader`（父加载器 = 系统类加载器）
  - `lib/` 目录存放项目运行时依赖（由 MANIFEST.MF Class-Path 管理，构建时固定）
  - `driver/` 目录专门存放用户自行放入的 JDBC 驱动，由 DriverClassLoader 动态扫描
  - `loadClass(className)` 委托父加载器优先，未找到时搜索 `driver/` 下的 JAR
- **DriverManager**: 通过 `driverClassLoader.loadClass(driverClass)` 加载驱动类 → 反射实例化 → `driver.connect()` 获取连接
- 通过 `driver.connect(url, info)` 直接建立 Connection（绕过 `java.sql.DriverManager`）
- `readOnly=true` 时自动调用 `conn.setReadOnly(true)`
- 每次调用返回新连接，无连接池

### 4.4 安全拦截

| 拦截器 | 类型 | 适用工具 | 生效条件 |
|--------|------|----------|----------|
| `QueryOnlyInterceptor` | 静态方法 | execute_query | 无条件生效，拒绝所有非 SELECT 语句 |
| `SqlInterceptor` | 有状态实例 | execute_update | 仅 `readOnly=true` 时生效 |

两者共用同一正则模式，拦截 7 个关键字：INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE。支持忽略前导空白和 `--` 单行注释。

### 4.5 结果格式化 (`ResultFormatter` + `HtmlTableBuilder`)

**execute_query** 输出 Markdown 格式：
- 输出分为 `# Schema` 和 `# Data` 两个部分
- Schema 部分列出列名、类型（`ResultSetMetaData.getColumnTypeName()`）、长度（`getColumnDisplaySize()`）
- Data 部分列出数据行
- NULL 值渲染为 `<i>NULL</i>`
- 非 NULL 值经 `escapeMarkdown()` 转义（`|` → `\|`，换行 → 空格）
- 双重行数限制：`Statement.setMaxRows()` + `ResultFormatter.format(rs, maxRows)` 遍历计数器

**execute_update** 输出纯文本：`"Statement executed successfully. Rows affected: N"`

**元数据查询工具**（list_catalogs 等）使用无行数限制的 `ResultFormatter.format(rs)` 重载，同样输出 Markdown 格式。

### 4.6 工具基类 (`AbstractMetaTool`)

- `buildSpecification()` 构建 `SyncToolSpecification`，自动包装 callHandler（try-catch + `ResultFormatter.errorResult()`）
- 子类实现 `doHandle()` 和可选的 `onError()`
- 提供 `getConnectionManager()` 和 `getOptionalString()` 工具方法

---

## 5. 配置与构建

### 5.1 config.yml 格式

项目提供 `config.example.yml` 作为模板，用户需复制为 `config.yml` 并填入实际配置（`config.yml` 已被 `.gitignore` 忽略，避免敏感信息入库）。

```yaml
datasources:
  - name: "prod_mysql"
    driver_class: "com.mysql.cj.jdbc.Driver"
    url: "jdbc:mysql://127.0.0.1:3306/prod_db?useSSL=false"
    username: "read_user"
    password: "secure_password_123"
    read_only: true
    max_rows: 50
```

### 5.2 启动命令

```bash
java -jar jdbc-mcp.jar <datasource_name>
```

### 5.3 分发包目录结构

```
jdbc-mcp-tool/
├── jdbc-mcp.jar        # 仅含项目业务逻辑的精简 JAR
├── config.example.yml  # 配置模板
├── config.yml          # 用户实际配置（不入版本控制）
├── driver/             # 用户自行放入的 JDBC 驱动
│   └── <用户自行放入的 JDBC 驱动>
├── lib/                # 运行时依赖（MANIFEST.MF Class-Path 管理）
│   ├── mcp-*.jar
│   ├── snakeyaml-*.jar
│   ├── slf4j-api-*.jar
│   ├── logback-classic-*.jar
│   ├── logback-core-*.jar
│   └── jackson-*.jar
└── logs/
    ├── jdbc-mcp.log
    └── jdbc-mcp.yyyy-MM-dd.log
```

### 5.4 Maven 插件

| 插件 | 用途 |
|------|------|
| `maven-jar-plugin` 3.4.1 | 精简 JAR，MANIFEST.MF 设置 mainClass + `Class-Path: lib/xxx.jar` |
| `maven-dependency-plugin` 3.7.1 | 拷贝运行时依赖到 `target/lib/` |
| `maven-assembly-plugin` 3.7.1 | 基于 `src/assembly/dist.xml` 打 zip 分发包 |
| `maven-compiler-plugin` 3.13.0 | Java 17 编译 + Lombok 注解处理器 |

### 5.5 日志配置 (`logback.xml`)

- 日志目录：`${app.home}/logs`（由 JdbcMcpServer 设置 `app.home` 系统属性）
- STDERR Appender：输出到 `System.err`（stdout 用于 MCP 通信，严禁写入）
- FILE Appender：每日滚动，保留 30 天，总大小上限 1GB
- 业务日志级别：`com.jdbcmcp` = INFO，`io.modelcontextprotocol` = WARN，root = WARN

---

## 6. 开发规范

1. **依赖隔离** — pom.xml 严禁引入数据库驱动依赖，驱动由用户自行放入 `driver/` 目录
2. **stdout 隔离 (CRITICAL)** — `System.out` 仅用于 MCP JSON-RPC 通信，严禁输出任何调试/日志/异常信息，全部走 `System.err` 或日志文件
3. **驱动加载** — 严禁使用 `java.sql.DriverManager.getConnection()`，必须通过 `DriverClassLoader.loadClass()` 加载驱动类 → 反射实例化 → `driver.connect()` 获取连接
4. **命令行参数** — `main()` 必须校验 `args[0]`，缺失或不匹配时 `System.exit(1)`
5. **安全拦截** — `execute_query` 天然只读（`QueryOnlyInterceptor` 无条件拦截）；`execute_update` 受 `read_only` 配置控制（`SqlInterceptor` 条件拦截 + `conn.setReadOnly()` 双重保护）
6. **行数截断** — `execute_query` 双重限制：`Statement.setMaxRows()` + 遍历计数器，默认 100 行
7. **Markdown 格式化** — `execute_query` 输出分为 `# Schema`（列名、类型、长度）和 `# Data`（数据行）两个部分，NULL 渲染为 `<i>NULL</i>`；`execute_update` 仅返回受影响行数
