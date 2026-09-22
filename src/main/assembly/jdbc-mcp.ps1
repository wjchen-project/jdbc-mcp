# ==============================================================================
# jdbc-mcp Windows 启动脚本（供 MCP 客户端以 stdio 方式调用）
#
# 用法：
#   1. 修改下方"用户配置区"中的连接参数
#   2. 在 MCP 客户端配置中指定本脚本，例如：
#        pwsh -NoProfile -File C:\path\to\jdbc-mcp-tool\jdbc-mcp.ps1
#
# 注意：
#   - stdout 仅用于 MCP JSON-RPC 协议通信，任何提示信息均输出到 stderr
#   - Java 查找顺序：配置区 $JavaPath > PATH 中的 java > JAVA_HOME
# ==============================================================================

# --------------------------------用户配置区------------------------------------

# JDBC 驱动类全限定名（驱动 JAR 需已放入同级 driver/ 目录）
$DriverClass = "com.mysql.cj.jdbc.Driver"

# JDBC 连接 URL
$JdbcUrl = "jdbc:mysql://127.0.0.1:3306/my_db?useSSL=false"

# 数据库用户名
$Username = "read_user"

# 数据库密码
$Password = "secure_password"

# execute_query 返回的最大行数
$MaxRows = 100

# 危险开关：允许 execute_update 执行写操作（默认 $false 只读）
$AllowWrite = $false

# Java 可执行文件路径；留空 $null 则自动从 PATH 或 JAVA_HOME 查找
$JavaPath = $null

# ------------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

# 定位脚本所在目录（即 jdbc-mcp.jar 所在目录）
$AppHome = Split-Path -Parent $MyInvocation.MyCommand.Path
$JarPath = Join-Path $AppHome "jdbc-mcp.jar"

function Write-Stderr([string]$Message) {
    [Console]::Error.WriteLine($Message)
}

# 前置检查：JAR 与 driver/ 目录必须存在
if (-not (Test-Path $JarPath)) {
    Write-Stderr "jdbc-mcp.ps1: JAR not found: $JarPath"
    exit 1
}
$DriverDir = Join-Path $AppHome "driver"
if (-not (Test-Path $DriverDir)) {
    Write-Stderr "jdbc-mcp.ps1: driver directory not found: $DriverDir"
    exit 1
}

# 解析 Java 可执行文件
if (-not $JavaPath) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $JavaPath = $javaCmd.Source
    } elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $JavaPath = Join-Path $env:JAVA_HOME "bin\java.exe"
    } else {
        Write-Stderr "jdbc-mcp.ps1: java not found in PATH or JAVA_HOME."
        exit 1
    }
}

# 组装启动参数
$args = @(
    "-Dapp.home=$AppHome",
    "-jar", $JarPath,
    "--driver-class", $DriverClass,
    "--url", $JdbcUrl,
    "--username", $Username,
    "--password", $Password,
    "--max-rows", $MaxRows
)
if ($AllowWrite) {
    $args += "--danger-allow-write"
}

# 启动 MCP Server（stdout/stderr 直接继承，stdio 协议流保持透传）
& $JavaPath @args
exit $LASTEXITCODE
