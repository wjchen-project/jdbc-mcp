#!/usr/bin/env bash
#
# jdbc-mcp 开发启动脚本
# 用法: ./run.sh <datasource_name>
#
# 自动编译项目、拼接 classpath，从项目根目录启动 MCP Server。
# driver/ 目录和 config.yml 均从项目根目录读取。
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 参数校验 ----------
if [ $# -eq 0 ]; then
    echo "Usage: ./run.sh <datasource_name>" >&2
    exit 1
fi

DATASOURCE="$1"

# ---------- 检查 config.yml ----------
if [ ! -f config.yml ]; then
    if [ -f config.example.yml ]; then
        echo "config.yml not found. Copying from config.example.yml..." >&2
        cp config.example.yml config.yml
        echo "Please edit config.yml with your datasource settings, then run again." >&2
        exit 1
    else
        echo "Error: config.yml not found. Please create it with your datasource configuration." >&2
        exit 1
    fi
fi

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
# JdbcMcpServer 和 ConfigManager 通过 getJarDir() 定位 config.yml 和 driver/
# 开发模式下 getJarDir() 返回 target/classes/，需要覆盖为项目根目录
export APP_HOME="$SCRIPT_DIR"

echo "Starting JDBC-MCP Server with datasource: $DATASOURCE"
echo "app.home = $APP_HOME"

exec java \
    -Dapp.home="$APP_HOME" \
    -cp "$CP" \
    com.jdbcmcp.JdbcMcpServer "$DATASOURCE"
