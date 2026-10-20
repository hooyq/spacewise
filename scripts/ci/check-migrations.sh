#!/usr/bin/env bash
# 检查 Flyway 迁移目录的改动是否合规（AGENTS.md 红线 1）。
# 用法：scripts/ci/check-migrations.sh [基准分支，默认 origin/main]
#   a) 相对基准分支，只允许新增迁移文件；修改、删除、重命名都视为违规
#   b) 同一服务内版本号不得重复
#   c) 文件名必须是 V<整数>__<小写字母数字下划线>.sql（Flyway 会静默忽略不合规的文件）
set -euo pipefail

base="${1:-origin/main}"
migration_glob=':(glob)services/*/src/main/resources/db/migration/**'
failed=0

echo "== a) 已有迁移文件不得修改、删除或重命名（相对 $base）"
violations=$(git diff --name-status --no-renames "$base"...HEAD -- "$migration_glob" | grep -v '^A' || true)
if [ -n "$violations" ]; then
  echo "违规：以下迁移文件被修改或删除。已合并的迁移只能新增新版本，不能改动："
  echo "$violations"
  failed=1
fi

for dir in services/*/src/main/resources/db/migration; do
  [ -d "$dir" ] || continue

  echo "== c) 文件命名：$dir"
  for file in "$dir"/*; do
    [ -e "$file" ] || continue
    name=$(basename "$file")
    if ! [[ "$name" =~ ^V[1-9][0-9]*__[a-z0-9_]+\.sql$ ]]; then
      echo "违规：$file 不符合 V<整数>__<小写描述>.sql，Flyway 不会执行它"
      failed=1
    fi
  done

  echo "== b) 版本号唯一：$dir"
  duplicates=$(find "$dir" -maxdepth 1 -name 'V*__*.sql' -printf '%f\n' | sed -E 's/^V([0-9]+)__.*/\1/' | sort | uniq -d)
  if [ -n "$duplicates" ]; then
    echo "违规：版本号重复：$duplicates（先同步目标分支，再把自己的迁移改成更大的版本号）"
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  exit 1
fi
echo "迁移检查通过"
