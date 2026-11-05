#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preview_java=${JAVA_HOME:+$JAVA_HOME/bin/java}
if [ -z "${preview_java:-}" ]; then
    preview_java=$(command -v java || true)
fi
if [ -z "$preview_java" ] || [ ! -x "$preview_java" ]; then
    echo "Galaxia GUI Preview requires JDK 25. Set JAVA_HOME or add java to PATH." >&2
    exit 2
fi

exec "$preview_java" "$repo_root/tools/gui-preview/PreviewBootstrap.java" "$repo_root" "$@"
