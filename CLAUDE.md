# JDBC-MCP Tool 技术方案与开发规范文档

本规范文档旨在定义一个基于 Model Context Protocol (MCP) 的通用 JDBC 数据库工具。该工具通过 `stdio` 方式与大模型（Agent）进行通信，支持多数据源管理、动态驱动加载、只读安全拦截以及格式化数据返回。

---

## 1. 需求分析 (Requirement Analysis)

### 1.1 核心功能

* **多数据源配置**：支持从 JAR 同级目录下的 `config.yml` 读取配置，可定义多个独立的数据源。
* **解耦的驱动管理**：项目本身不打包任何特定数据库的 JDBC 驱动（如 MySQL, PostgreSQL, Oracle 等）。用户将所需的 JDBC 驱动 JAR 放入 `lib/` 目录，由系统类加载器通过 MANIFEST.MF 的 `Class-Path` 统一加载。
* **MCP 协议通信**：基于 `stdio`（标准输入/输出）实现 MCP 的 JSON-RPC 协议，向 Agent 提供数据库探查和操作能力。
* **核心工具能力 (Tools)**：
1. `get_schema`：获取当前数据源的所有表结构、字段、类型等元数据。
2. `execute_query`：执行只读查询 SQL（SELECT），天然只读，返回 HTML Table 格式结果。
3. `execute_update`：执行数据修改 SQL（INSERT/UPDATE/DELETE/DDL），返回受影响行数。在 read_only 模式下被拦截拒绝。



### 1.2 数据安全与性能控制

* **只读开关保护 (`read_only`)**：支持为每个数据源单独配置是否只读。若开启，须在连接层和应用层进行双重拦截，严禁执行 DML（增删改）和 DDL（结构变更）语句。
* **数据行数限制 (`max_rows`)**：为防止大数据量撑爆大模型的上下文窗口，默认查询结果最多返回 100 条。该数值支持在数据源配置中进行个性化覆盖。

### 1.3 格式化输出

* 查询结果必须转换为类 HTML 的 `<table>` 纯文本格式返回给 Agent。
* 为了让 Agent 更好地理解数据，表头 `<th>` 必须附带字段的原始类型（Type）和长度（Length）等元数据属性。

---

## 2. 系统架构设计 (System Architecture Design)

### 2.1 类加载架构 (Class Loading Architecture)

项目采用系统类加载器统一管理所有 JAR 的加载方式：

* **系统类加载器（AppClassLoader）**：负责加载 `jdbc-mcp.jar` 本身，并通过 MANIFEST.MF 中的 `Class-Path` 属性加载 `lib/` 目录下的所有依赖 JAR（包括运行时依赖和用户放入的 JDBC 驱动）。所有类由同一个类加载器加载，无隔离问题。
* **JDBC 驱动实例化**：通过 `Class.forName(driverClass)` 加载驱动类，反射调用无参构造函数生成 `java.sql.Driver` 实例，再显式调用 `driver.connect(url, info)` 获取数据库连接，绕过 `java.sql.DriverManager` 机制。

### 2.2 核心模块划分

1. **通信与路由模块 (Transport & Router)**：监听 `System.in`，解析符合 MCP 规范的 JSON-RPC 请求，并根据 `method` 路由至具体的 Tool 实现。
2. **配置管理模块 (Config Manager)**：负责在启动时解析 JAR 同级目录下的 `config.yml`，若文件不存在或格式错误则优雅报错。
3. **连接工厂模块 (Connection Factory)**：通过反射实例化 `java.sql.Driver`，绕过 `DriverManager` 机制直接建立 `Connection`。
4. **安全拦截模块 (Interceptor)**：对执行的 SQL 进行前置语法检查和关键字过滤。包含两种拦截器：`SqlInterceptor`（基于 read_only 配置拦截 DML/DDL，用于 execute_update）和 `QueryOnlyInterceptor`（无条件仅允许 SELECT，用于 execute_query）。
5. **结果集格式化模块 (Result Formatter)**：将 `ResultSet` 转换为带有元数据属性的 HTML 字符串（用于 execute_query）；execute_update 仅返回受影响行数。

---

## 3. 接口与配置规范 (Specification)

### 3.1 配置文件规范 (`config.yml`，JAR 同级目录)

配置文件位于 JAR 包同级目录下（即 `jdbc-mcp.jar` 旁边），路径无需用户手动指定，程序启动时自动定位。

```yaml
datasources:
  - name: "prod_mysql"
    driver_class: "com.mysql.cj.jdbc.Driver"
    url: "jdbc:mysql://127.0.0.1:3306/prod_db?useSSL=false"
    username: "read_user"
    password: "secure_password_123"
    read_only: true
    max_rows: 50

  - name: "dev_postgres"
    driver_class: "org.postgresql.Driver"
    url: "jdbc:postgresql://localhost:5432/dev_db"
    username: "postgres"
    password: "dev_password"
    read_only: false
    # max_rows 未配置时，应用层默认限制为 100

```

### 3.2 命令行启动规范

在给 Agent 配置该 MCP 工具时，必须通过命令行参数显式指定目标数据源名称。

```bash
java -jar jdbc-mcp.jar <datasource_name>

```

* 示例：`java -jar jdbc-mcp.jar prod_mysql`
* 启动时，程序必须匹配传入的 `name`，若未传参数或未匹配到相应数据源，须向 `System.err` 打印错误并退出。

### 3.3 输出格式化规范 (HTML Table Meta)

查询结果返回给 MCP Client 的文本必须遵循以下 HTML 结构：

```html
<table border="1">
  <thead>
    <tr>
      <th data-type="VARCHAR" data-length="64">user_id</th>
      <th data-type="INT" data-length="11">age</th>
      <th data-type="TIMESTAMP" data-length="0">login_time</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>USR_001</td>
      <td>28</td>
      <td>2026-06-29 10:15:00</td>
    </tr>
    <tr>
      <td>USR_002</td>
      <td><i>NULL</i></td>
      <td>2026-06-29 11:20:14</td>
    </tr>
  </tbody>
</table>

```

> **注意**：若字段值为 `null`，应用层须将其统一渲染为 `<i>NULL</i>`。

---

## 4. 项目构建与分发

### 4.1 目录结构标准

项目采用 Maven 进行管理，最终交付给用户解压后的目录结构必须如下所示：

```text
jdbc-mcp-tool/
├── jdbc-mcp.jar        # 仅含项目自身业务逻辑的精简 JAR
├── config.yml          # 配置文件（JAR 同级目录）
├── lib/                # 运行时依赖 + 用户放入的数据库驱动
│   ├── mcp-*.jar               # MCP SDK 依赖
│   ├── snakeyaml-*.jar         # YAML 解析
│   ├── slf4j-api-*.jar         # SLF4J API
│   ├── logback-classic-*.jar   # Logback
│   ├── logback-core-*.jar      # Logback Core
│   ├── jackson-*.jar           # Jackson（MCP SDK 传递依赖）
│   └── mysql-connector-j.jar   # 用户自行放入的 JDBC 驱动（示例）
└── logs/               # 日志目录，程序运行时自动创建
    ├── jdbc-mcp.log            # 当前日志
    └── jdbc-mcp.2026-06-29.log # 每日滚动归档
```

### 4.2 Maven 插件配置要点

* 使用 `maven-jar-plugin` 构建仅含项目自身 class 的精简 JAR，MANIFEST.MF 中通过 `Class-Path: lib/xxx.jar` 引用外部依赖。
* 使用 `maven-dependency-plugin` 将所有运行时依赖拷贝到 `target/lib/`。
* 使用 `maven-assembly-plugin` 打包分发包，将项目 JAR、依赖 lib/、配置文件打包为 tar.gz。

---

## 5. 项目开发规范 (LLM Prompt Constraints)

你正在作为一个资深的 Java 架构师与编码专家，编写一个基于 JDBC 的 MCP (Model Context Protocol) 工具。请严格遵守以下开发规范，任何违反以下规范的代码都将被视为 Bug。

1. 依赖隔离与构建 (Maven)
- 严禁在 pom.xml 中引入任何具体的数据库驱动依赖（如 mysql-connector-java、postgresql 等）。
- 仅允许引入核心通用库：如 SnakeYAML（解析配置）、Jackson/Gson（解析 JSON）、SLF4J/Logback（日志）、以及基础的 MCP 协议支持库。
- 构建时使用 maven-jar-plugin 生成仅含项目自身 class 的精简 JAR，MANIFEST.MF 中通过 `Class-Path: lib/xxx.jar` 引用外部依赖（不含 JDBC 驱动）。
- 使用 maven-dependency-plugin 将运行时依赖拷贝到 `target/lib/`，分发包中依赖与 JDBC 驱动共存于同一 `lib/` 目录。

2. 标准输出绝对隔离 (CRITICAL)
- 由于此程序通过 stdio (标准输入/输出) 与 MCP 客户端进行 JSON-RPC 通信，System.out 必须且只能用于输出符合 MCP 协议的 JSON 字符串。
- 严禁使用 System.out.println() 打印任何调试信息、欢迎语、业务日志或异常堆栈。
- 所有的日志记录（INFO, DEBUG, ERROR）以及代码异常堆栈（e.printStackTrace()），必须全部重定向输出到 System.err，或写入到指定的独立日志文件中。

3. JDBC 驱动加载
- 严禁使用 java.sql.DriverManager.getConnection()，因为标准驱动管理器的自动发现机制在非标准部署场景下可能不可靠。
- 必须实现以下加载逻辑：
  a. 通过 Class.forName(driverClass) 使用系统类加载器加载驱动类。
  b. 通过反射调用其无参构造函数生成 java.sql.Driver 实例。
  c. 显式调用 driver.connect(url, info) 获取数据库 Connection 对象。

4. 命令行参数与初始化
- main(String[] args) 方法必须强制校验 args[0] 为数据源名称（datasource name）。
- 如果未传入参数，或者传入的名称在 config.yml 中不存在，程序必须向 System.err 输出错误原因，并调用 System.exit(1) 非零状态码退出。

5. 安全防护拦截 (Read-Only)
- execute_query 工具天然只读，始终仅允许 SELECT 语句，无论数据源是否配置 read_only。非 SELECT 语句（INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE）一律拒绝。
- execute_update 工具用于执行数据修改操作（INSERT, UPDATE, DELETE, DDL）。当对应的配置项 read_only: true 时，必须实施双重保护：
  a. 获取连接后，立即执行 connection.setReadOnly(true)。
    b. 在执行 SQL 前，在应用层对 SQL 字符串进行前置正则或词法分析。如果包含 INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE 等非查询关键字，必须直接拒绝执行，并将错误信息封装为 MCP Error 响应返回，严禁让进程崩溃退出。

6. 数据量阈值截断 (Max Rows)
- execute_query 每次执行查询时，必须显式调用 Statement.setMaxRows(limit)。limit 取自数据源配置，若未配置则默认为 100。
- 遍历 ResultSet 时，应用层必须维护一个整型计数器。一旦达到 limit 阈值，必须立即 break 终止遍历，防止过量数据占用内存和模型上下文。

7. 结果集 HTML 格式化
- execute_query 查询返回的 Text 必须是一段符合标准的 <table> 结构。
- 通过 ResultSetMetaData 动态提取字段的本地类型名称 (getColumnTypeName) 和列显示大小 (getColumnDisplaySize)。
- 表头必须输出为 <th data-type="类型" data-length="长度">列名</th> 格式。
- 若数据库字段值为 null，HTML 中必须将其渲染为 <i>NULL</i>。
- execute_update 不返回 HTML Table，仅返回受影响行数（如 "Rows affected: 3"）。
