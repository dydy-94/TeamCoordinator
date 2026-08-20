#!/usr/bin/env bash
# 安装 tc CLI（TeamCoordinator 配套命令行工具）
#
# 用法:
#   ./install.sh                 # 安装到 ~/bin/tc
#   TC_INSTALL_DIR=/usr/local/bin ./install.sh   # 安装到指定目录
#
# 前置: Node 18+（tc.mjs 零 npm 依赖，仅用内置 fetch/fs）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${TC_INSTALL_DIR:-$HOME/bin}"

if ! command -v node >/dev/null 2>&1; then
    echo "错误: 未找到 node，tc 需要 Node 18+（内置 fetch）" >&2
    exit 1
fi
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if [ "$NODE_MAJOR" -lt 18 ]; then
    echo "错误: node 版本过低（$NODE_MAJOR），需要 18+" >&2
    exit 1
fi

mkdir -p "$INSTALL_DIR"
cp "$SCRIPT_DIR/tc.mjs" "$INSTALL_DIR/tc"
chmod +x "$INSTALL_DIR/tc"

echo "✔ tc 已安装到 $INSTALL_DIR/tc（node v$(node -p 'process.versions.node')）"
case ":$PATH:" in
    *":$INSTALL_DIR:"*) ;;
    *)
        echo "  请把安装目录加入 PATH（如 ~/.zshrc 或 ~/.bashrc）："
        echo "  export PATH=\"$INSTALL_DIR:\$PATH\""
        ;;
esac
echo
echo "配置 TeamCoordinator 连接（同样写入 shell 配置）："
echo "  export TC_BASE_URL=\"http://127.0.0.1:8080\""
echo "  export TC_TOKEN=\"<与 TeamCoordinator 的 AGENTCORE_ARTIFACT_TOOL_TOKEN 一致>\""
echo
echo "验证:"
echo "  source ~/.zshrc   # 或重新开终端"
echo "  tc health"
