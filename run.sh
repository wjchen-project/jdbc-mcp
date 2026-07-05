#!/usr/bin/env bash
#
# jdbc-mcp 开发启动脚本
# 用法:
#   ./run.sh \
#     --driver-class <jdbc_driver_class> \
#     --url <jdbc_url> \
#     --username <username> \
#     --password <password> \
#     [--danger-allow-write] \
#     [--max-rows <positive_integer>]
#
# 自动编译项目、拼接 classpath，从项目根目录启动 MCP Server。
# driver/ 目录从项目根目录读取。
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 编译 ----------
echo "Compiling project..."
mvn compile -q

# ---------- 拼接 classpath ----------
# 项目编译输出
CP="target/classes"

# 运行时依赖
DEP_CLASSPATH=$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)
CP="$CP:$DEP_CLASSPATH"

# driver/ 目录下的 JDBC 驱动
if [ -d driver ]; then
    for jar in driver/*.jar; do
        [ -f "$jar" ] && CP="$CP:$jar"
    done
fi

# ---------- 设置 app.home 指向项目根目录 ----------
# JdbcMcpServer 通过 getJarDir() 定位 driver/
# 开发模式下 getJarDir() 返回 target/classes/，需要覆盖为项目根目录
export APP_HOME="$SCRIPT_DIR"

echo "Starting JDBC-MCP Server..."
echo "app.home = $APP_HOME"

exec java \
    -Dapp.home="$APP_HOME" \
    -cp "$CP" \
    com.jdbcmcp.JdbcMcpServer "$@"
