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
├── jdbc-mcp.jar        # 精简 JAR（仅含项目业务逻辑）
├── config.yml          # 配置文件
├── lib/                # 运行时依赖 + 用户放入的数据库驱动
│   ├── mcp-*.jar
│   ├── snakeyaml-*.jar
│   ├── slf4j-api-*.jar
│   ├── logback-classic-*.jar
│   ├── logback-core-*.jar
│   ├── jackson-*.jar
│   └── <用户自行放入的 JDBC 驱动>
└── logs/               # 日志目录（运行时自动创建）
```

### 3. 配置数据源

编辑 `config.yml`：

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

本工具向 Agent 注册以下 6 个 MCP 工具：

### 元数据探查

| 工具 | 说明 |
|------|------|
| `list_catalogs` | 列出所有 catalog |
| `list_schemas` | 列出 schema，可按 catalog 过滤 |
| `list_tables` | 列出表和视图，可按 catalog / schema / 表名过滤 |
| `get_table_schema` | 获取指定表的列元数据（类型、长度等） |

### 数据操作

| 工具 | 说明 |
|------|------|
| `execute_query` | 执行只读查询 SQL（SELECT），天然只读，返回 HTML Table 格式结果，受 `max_rows` 限制 |
| `execute_update` | 执行数据修改 SQL（INSERT/UPDATE/DELETE/DDL），返回受影响行数。`read_only` 模式下被拦截拒绝 |

#### execute_query 输出示例

```html
<table border="1">
  <thead>
    <tr>
      <th data-type="VARCHAR" data-length="64">user_id</th>
      <th data-type="INT" data-length="11">age</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>USR_001</td>
      <td>28</td>
    </tr>
    <tr>
      <td>USR_002</td>
      <td><i>NULL</i></td>
    </tr>
  </tbody>
</table>
```

## 配置规范

| 字段 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| `name` | 数据源名称，用于命令行参数指定 | 是 | - |
| `driver_class` | JDBC 驱动全限定类名 | 是 | - |
| `url` | JDBC 连接 URL | 是 | - |
| `username` | 数据库用户名 | 是 | - |
| `password` | 数据库密码 | 是 | - |
| `read_only` | 是否只读模式（影响 `execute_update`） | 否 | `false` |
| `max_rows` | 查询结果最大返回行数（影响 `execute_query`） | 否 | `100` |

## 开发规范

详见 [CLAUDE.md](CLAUDE.md)。
