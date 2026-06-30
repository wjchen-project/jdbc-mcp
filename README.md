# JDBC-MCP Tool

基于 Model Context Protocol (MCP) 的通用 JDBC 数据库工具，通过 `stdio` 方式与大模型 Agent 进行通信，支持多数据源管理、动态驱动加载、只读安全拦截以及格式化数据返回。

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

构建完成后，分发包位于 `target/jdbc-mcp-tool.tar.gz`。

### 2. 安装

```bash
tar -xzf target/jdbc-mcp-tool.tar.gz -C ~/jdbc-mcp-tool
```

解压后目录结构：
```
jdbc-mcp-tool/
├── jdbc-mcp.jar        # Fat-JAR（包含核心逻辑及基础依赖）
├── config.yml          # 配置模板文件
└── lib/                # 用户在此放入所需的数据库驱动 JAR
```

### 3. 配置数据源c

将 `config.yml` 复制到 `~/.config/jdbc-mcp/config.yml` 并编辑：

```yaml
datasources:
  - name: "my_db"
    driver_class: "com.mysql.cj.jdbc.Driver"
    url: "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false"
    username: "read_user"
    password: "secure_password"
    read_only: true
    max_rows: 50
```

### 4. 放置 JDBC 驱动

将数据库驱动 JAR 文件放入 `lib/` 目录，例如：
```bash
cp mysql-connector-j-8.x.jar ~/jdbc-mcp-tool/lib/
```

### 5. 启动

```bash
java -jar jdbc-mcp.jar my_db
```

## MCP 工具说明

### `get_schema`
获取当前数据源的所有表结构、字段、类型等元数据。

### `execute_sql`
执行 Agent 传入的 SQL 语句。在只读模式下，DML 和 DDL 语句将被拒绝。

## 配置规范

| 字段 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| `name` | 数据源名称，用于命令行参数指定 | 是 | - |
| `driver_class` | JDBC 驱动全限定类名 | 是 | - |
| `url` | JDBC 连接 URL | 是 | - |
| `username` | 数据库用户名 | 是 | - |
| `password` | 数据库密码 | 是 | - |
| `read_only` | 是否只读模式 | 否 | `false` |
| `max_rows` | 查询结果最大返回行数 | 否 | `100` |

## 开发规范

详见 [CLAUDE.md](CLAUDE.md)。
