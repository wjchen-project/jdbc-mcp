# JDBC-MCP Tool

基于 Model Context Protocol (MCP) 的通用 JDBC 数据库工具，通过 `stdio` 方式与大模型 Agent 进行通信，支持命令行参数加载、动态驱动加载、只读安全拦截以及格式化数据返回。

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

构建完成后，分发包位于 `target/jdbc-mcp-tool.zip`。

### 2. 安装

```bash
unzip target/jdbc-mcp-tool.zip -d ~/
```

解压后目录结构：
```
jdbc-mcp-tool/
├── jdbc-mcp.jar        # 精简 JAR（仅含项目业务逻辑）
├── driver/             # 用户放入的数据库驱动
│   └── <用户自行放入的 JDBC 驱动>
├── lib/                # 运行时依赖（由 MANIFEST.MF 管理）
│   ├── mcp-*.jar
│   ├── slf4j-api-*.jar
│   ├── logback-classic-*.jar
│   ├── logback-core-*.jar
│   └── jackson-*.jar
└── logs/               # 日志目录（运行时自动创建）
```

### 3. 准备启动参数

本工具不再读取 `config.yml`，所有数据库连接参数均通过命令行传入：

```bash
java -jar jdbc-mcp.jar \
  --driver-class com.mysql.cj.jdbc.Driver \
  --url "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false" \
  --username read_user \
  --password secure_password \
  --max-rows 50
```

参数同时支持已注册的短指令和 `--key=value` 写法，例如：`-d com.mysql.cj.jdbc.Driver` 或 `--driver-class=com.mysql.cj.jdbc.Driver`。
默认禁止写操作；如需允许 `execute_update` 执行修改语句，可显式追加布尔开关 `--danger-allow-write`（该开关不需要参数值）。

### 4. 放置 JDBC 驱动

将数据库驱动 JAR 文件放入 `driver/` 目录，例如：
```bash
cp mysql-connector-j-8.x.jar ~/jdbc-mcp-tool/driver/
```

### 5. 配置 MCP 客户端

本工具通过 `stdio` 方式与 MCP 客户端通信，以下以 **Claude Code** 为例说明配置方法。

#### Claude Code

在项目根目录创建 `.mcp.json` 文件：

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

> **提示**：将 `/path/to/jdbc-mcp-tool/` 替换为实际安装路径，并按实际数据库连接信息填写参数。

配置完成后，Claude Code 会自动加载 MCP 服务器，即可在对话中使用数据库工具。

#### 开发模式

开发阶段可使用项目自带的 `run.sh` 脚本（自动编译、拼接 classpath）：

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

## MCP 工具说明

本工具向 Agent 注册以下 6 个 MCP 工具：

### 元数据探查

| 工具 | 说明 |
|------|------|
| `list_catalogs` | 列出所有 catalog |
| `list_schemas` | 列出 schema，可按 catalog 过滤 |
| `list_tables` | 列出表和视图，可按 catalog / schema / 表名过滤 |
| `get_table_schema` | 获取指定表的列元数据（类型、长度、可空、主键、自增等） |

### 数据操作

| 工具 | 说明 |
|------|------|
| `execute_query` | 执行只读查询 SQL（SELECT），天然只读，返回 Markdown 格式结果，受 `--max-rows` 限制 |
| `execute_update` | 执行数据修改 SQL（INSERT/UPDATE/DELETE/DDL），返回受影响行数。默认被只读模式拦截，需显式指定 `--danger-allow-write` 才允许写操作 |

#### execute_query 输出示例

输出分为 `# Schema`（列名、类型、长度）和 `# Data`（数据行）两个部分，NULL 值渲染为 `<i>NULL</i>`。

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

## 命令行参数规范

| 长指令 | 短指令 | 说明 | 必填 | 默认值 |
|--------|--------|------|------|--------|
| `--driver-class` | `-d` | JDBC 驱动全限定类名 | 是 | - |
| `--url` | - | JDBC 连接 URL | 是 | - |
| `--username` | `-u` | 数据库用户名 | 是 | - |
| `--password` | `-p` | 数据库密码 | 是 | - |
| `--danger-allow-write` | - | 是否允许写操作；布尔开关，不需要参数值 | 否 | `false` |
| `--max-rows` | `-m` | 查询结果最大返回行数（影响 `execute_query`） | 否 | `100` |
