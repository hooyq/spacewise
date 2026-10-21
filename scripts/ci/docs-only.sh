#!/usr/bin/env bash
# 判断改动是否只涉及文档（docs/ 下的文件或 *.md），输出 docs_only=true|false，供 CI 跳过编译和测试。
# 用法：scripts/ci/docs-only.sh [基准分支，默认 origin/dev]
# 判断保守：没有改动、或有任何一个非文档文件，都输出 false（照常构建）。
set -euo pipefail

base="${1:-origin/dev}"
changed=$(git diff --name-only "$base"...HEAD)

echo "相对 $base 的改动：" >&2
echo "${changed:-（无）}" >&2

if [ -n "$changed" ] && ! grep -qvE '^docs/|\.md$' <<< "$changed"; then
  echo "docs_only=true"
else
  echo "docs_only=false"
fi
