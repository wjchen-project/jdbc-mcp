# JDBC-MCP Tool

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-orange" alt="Java 17+" />
  <img src="https://img.shields.io/badge/build-Maven-blue" alt="Maven" />
  <img src="https://img.shields.io/badge/MCP-stdio-7c3aed" alt="MCP stdio" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License" /></a>
</p>

<p align="center">
  <b>一个面向 AI Agent 的通用 JDBC 数据库 MCP Server。</b>
  <br />
  通过标准 <code>stdio</code> 传输协议连接 MCP 客户端，让 Agent 安全地探查数据库结构并执行受控 SQL。
</p>

---

## ✨ 特性亮点

| 特性 | 说明 |
|------|------|
| 🔌 通用 JDBC | 不绑定特定数据库，理论支持所有提供标准 JDBC Driver 的数据库 |
| 🧩 MCP 标准接入 | 基于 Model Context Protocol，通过 `stdio` 与 Claude Code 等客户端通信 |
| 🛡️ 默认只读安全 | `execute_query` 仅允许 `SELECT`；写操作默认拦截，需显式开启 |
| 📦 驱动动态加载 | 数据库驱动独立放入 `driver/` 目录，项目本身不内置数据库驱动 |
| ♻️ 连接池复用 | 基于 HikariCP 连接池管理数据库连接，避免 Agent 高频调用时反复建连或泄漏连接 |
| 📊 Markdown 输出 | 查询结果自动格式化为 `# Schema` 与 `# Data`，便于 Agent 阅读 |
| ⚙️ 命令行配置 | 无需配置文件，连接信息、行数限制、写入开关均通过启动参数指定 |
| 📤 异步 XLSX 导出 | 基于 Apache Fesod Sheet 后台执行 SELECT 导出任务，支持通过任务 ID 查询状态、已导出行数和取消任务 |

## 📚 目录

- [快速开始](#-快速开始)
- [配置 MCP 客户端](#-配置-mcp-客户端)
- [MCP 工具说明](#-mcp-工具说明)
- [命令行参数](#-命令行参数)
- [安全设计](#-安全设计)
- [项目结构](#-项目结构)
- [开发与构建](#-开发与构建)
- [常见问题](#-常见问题)
- [开源许可](#-开源许可)

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- 目标数据库对应的 JDBC Driver JAR

### 1. 构建项目

```bash
mvn clean package
```

构建完成后，分发包位于：

```text
target/jdbc-mcp.zip
```

> GitHub Release 中的分发包可能按标签重命名为 `jdbc-mcp-<tag>.zip`；本地构建产物固定为 `target/jdbc-mcp.zip`。

### 2. 安装分发包

```bash
unzip target/jdbc-mcp.zip -d ~/
```

解压后目录结构如下：

```text
jdbc-mcp-tool/
├── jdbc-mcp.jar        # 精简 JAR，仅包含项目自身业务逻辑
├── LICENSE             # MIT 开源许可证
├── driver/             # 用户自行放入数据库 JDBC 驱动
│   └── <your-jdbc-driver>.jar
├── lib/                # 运行时依赖，由 MANIFEST.MF Class-Path 管理
│   ├── mcp-*.jar
│   ├── slf4j-api-*.jar
│   ├── logback-classic-*.jar
│   ├── logback-core-*.jar
│   ├── fesod-*.jar
│   ├── poi-*.jar
│   └── jackson-*.jar
└── logs/               # 日志目录，首次运行时自动创建
```

### 3. 放置 JDBC 驱动

将目标数据库的 JDBC Driver JAR 放入 `driver/` 目录，例如 MySQL：

```bash
cp mysql-connector-j-8.x.jar ~/jdbc-mcp-tool/driver/
```

> 本项目刻意不内置任何数据库驱动，避免不必要的依赖膨胀与许可证耦合。

### 4. 测试启动参数

```bash
java -jar ~/jdbc-mcp-tool/jdbc-mcp.jar \
  --driver-class com.mysql.cj.jdbc.Driver \
  --url "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false" \
  --username read_user \
  --password secure_password \
  --max-rows 50
```

参数同时支持短指令与 `--key=value` 写法，例如：

```bash
java -jar jdbc-mcp.jar \
  -d com.mysql.cj.jdbc.Driver \
  --url=jdbc:mysql://127.0.0.1:3306/my_db \
  -u read_user \
  -p secure_password \
  -m 50
```

## 🔗 配置 MCP 客户端

本工具通过 `stdio` 与 MCP 客户端通信。以下以 **Claude Code** 为例。

### 生产/日常使用

在项目根目录创建 `.mcp.json`：

```json
{
  "mcpServers": {
    "jdbc-mcp": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/path/to/jdbc-mcp-tool/jdbc-mcp.jar",
        "--driver-class", "com.mysql.cj.jdbc.Driver",
        "--url", "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false",
        "--username", "read_user",
        "--password", "secure_password",
        "--max-rows", "50"
      ]
    }
  }
}
```

> 将 `/path/to/jdbc-mcp-tool/` 替换为实际安装路径，并按实际数据库连接信息填写参数。

### 开发模式

开发阶段可直接使用仓库内的 `run.sh` 脚本，它会自动编译并拼接 classpath：

```json
{
  "mcpServers": {
    "jdbc-mcp": {
      "type": "stdio",
      "command": "./run.sh",
      "args": [
        "--driver-class", "com.mysql.cj.jdbc.Driver",
        "--url", "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false",
        "--username", "read_user",
        "--password", "secure_password",
        "--max-rows", "50"
      ]
    }
  }
}
```

配置完成后，MCP 客户端即可发现并调用数据库工具。

## 🧰 MCP 工具说明

本工具向 Agent 注册 10 个 MCP 工具。

### 元数据探查

| 工具 | 参数 | 说明 |
|------|------|------|
| `list_catalogs` | 无 | 列出所有 catalog |
| `list_schemas` | `catalog?` | 列出 schema，可按 catalog 过滤 |
| `list_tables` | `catalog?`, `schema?`, `table_pattern?` | 列出表、视图和系统表，可按 catalog / schema / 表名过滤 |
| `get_table_schema` | `catalog?`, `schema?`, `table?` | 获取表字段元数据，包括类型、长度、小数位数、可空、主键、自增、默认值、备注等 |

### SQL 执行

| 工具 | 参数 | 说明 |
|------|------|------|
| `execute_query` | `sql` | 执行只读查询 SQL，仅允许 `SELECT`，返回 Markdown 格式结果，受 `--max-rows` 限制 |
| `execute_update` | `sql` | 执行 `INSERT` / `UPDATE` / `DELETE` / DDL 等修改语句；默认被只读模式拦截，需显式开启写权限 |

### 异步 XLSX 导出

| 工具 | 参数 | 说明 |
|------|------|------|
| `start_export_task` | `sql`, `file_path` | 启动后台 XLSX 导出任务；`sql` 仅允许 `SELECT`，`file_path` 必须以 `.xlsx` 结尾 |
| `get_export_task` | `task_id` | 查询导出任务状态、已导出行数、输出路径、时间戳与错误信息 |
| `list_export_tasks` | 无 | 列出当前 MCP Server 进程内的导出任务 |
| `cancel_export_task` | `task_id` | 尽力取消长时间运行的导出任务 |

导出任务不会占用 MCP 单次调用等待时间：`start_export_task` 会立即返回任务 ID，后台线程持续流式读取 `ResultSet`，并通过 Apache Fesod Sheet 按批次写入 XLSX。进度以 `exportedRows`（已导出数据行数，不含表头）呈现；单个工作表达到 Excel 行数上限后会自动切换到下一张工作表。

示例返回：

```markdown
# Export Task

- **Task ID:** 5c2d...
- **Status:** RUNNING
- **Exported Rows:** 12000
- **File Path:** /tmp/users.xlsx
```

### 查询输出示例

`execute_query` 输出分为 `# Schema` 与 `# Data` 两部分，`NULL` 值渲染为 `<i>NULL</i>`。

```markdown
# Schema
| Name | Type | Length |
| --- | --- | --- |
| user_id | VARCHAR | 64 |
| age | INT | 11 |

# Data
| user_id | age |
| --- | --- |
| USR_001 | 28 |
| USR_002 | <i>NULL</i> |
```

## ⚙️ 命令行参数

| 长指令 | 短指令 | 说明 | 必填 | 默认值 |
|--------|--------|------|------|--------|
| `--driver-class` | `-d` | JDBC 驱动全限定类名 | 是 | - |
| `--url` | - | JDBC 连接 URL | 是 | - |
| `--username` | `-u` | 数据库用户名 | 是 | - |
| `--password` | `-p` | 数据库密码 | 是 | - |
| `--max-rows` | `-m` | `execute_query` 最大返回行数 | 否 | `100` |
| `--danger-allow-write` | - | 允许 `execute_update` 执行写操作；布尔开关，不需要参数值 | 否 | `false` |

## 🛡️ 安全设计

JDBC-MCP 默认面向数据库探查与只读查询场景，安全策略如下：

1. **查询工具天然只读**：`execute_query` 始终拒绝非 `SELECT` 语句。
2. **写操作显式授权**：`execute_update` 默认拦截修改语句，仅在启动时添加 `--danger-allow-write` 后允许执行。
3. **数据库连接只读保护**：只读模式下通过 HikariCP `isReadOnly` 配置在连接入池时调用 `Connection#setReadOnly(true)`，由数据库驱动与服务端共同约束。
4. **行数限制**：`execute_query` 同时通过 `Statement#setMaxRows()` 与格式化遍历计数限制返回行数。
   异步导出不受 `--max-rows` 限制，采用 JDBC fetch size 与 XLSX 流式写入，适合大结果集长时间任务。
5. **stdout 隔离**：标准输出仅用于 MCP JSON-RPC 通信；日志输出到 stderr 与日志文件，避免污染协议流。

如需开启写操作，请明确承担风险并使用权限受限的数据库账号：

```bash
java -jar jdbc-mcp.jar \
  --driver-class com.mysql.cj.jdbc.Driver \
  --url "jdbc:mysql://127.0.0.1:3306/my_db" \
  --username write_user \
  --password secure_password \
  --danger-allow-write
```

## 🏗️ 项目结构

```text
src/main/java/com/jdbcmcp/
├── JdbcMcpServer.java                  # 入口类，MCP Server 构建与工具注册
├── config/                             # 命令行参数解析与数据源配置
├── connection/                         # JDBC 驱动加载与连接管理
├── formatter/                          # ResultSet -> Markdown 格式化
├── interceptor/                        # SQL 只读/写入拦截器
├── exporter/                          # 异步 XLSX 导出任务与流式写入
└── tool/                               # MCP 工具实现
```

## 🧑‍💻 开发与构建

常用命令：

```bash
# 编译、测试并打包分发 zip
mvn clean package

# 仅校验 Maven 配置
mvn -DskipTests validate

# 开发模式启动 MCP Server
./run.sh --driver-class <jdbc_driver_class> \
  --url <jdbc_url> \
  --username <username> \
  --password <password>
```

提交代码时建议遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat: add new database metadata tool
fix: handle nullable column metadata safely
docs: improve quick start guide
```

## ❓ 常见问题

### 为什么不直接把 MySQL / PostgreSQL 驱动打进包里？

不同数据库驱动版本、许可证与用户环境差异较大。JDBC-MCP 将驱动放在外部 `driver/` 目录动态加载，让用户自行选择数据库驱动版本。

### 默认可以执行 UPDATE / DELETE 吗？

不可以。默认只读模式会拦截写操作。如确实需要执行修改语句，启动时添加 `--danger-allow-write`，并建议使用低权限账号与测试环境。

### 查询返回太多数据怎么办？

通过 `--max-rows` 控制 `execute_query` 最大返回行数，默认 `100`。

### 可以连接哪些数据库？

只要数据库提供标准 JDBC Driver，并且驱动 JAR 放入 `driver/` 目录，通常都可以连接。具体能力取决于数据库驱动对 JDBC 元数据接口的支持程度。

## 📄 开源许可

本项目采用 [MIT License](LICENSE) 开源许可。你可以自由使用、复制、修改、合并、发布、分发、再授权和销售本项目代码；使用时需保留原始版权声明和许可证文本。
