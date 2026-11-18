#!/usr/bin/env bash
# 重置本地开发数据库（清空后由后端重启时自动重建表结构并写入种子数据）
set -e
export PGPASSWORD=${DB_PASS:-ccdev}
PSQL=/opt/postgresql-18/bin/psql
$PSQL -h ${DB_HOST:-127.0.0.1} -U ${DB_USER:-ccdev} -d ${DB_NAME:-ccdev} \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
echo "数据库已清空。重启后端后将自动重建表结构并初始化样例数据。"
