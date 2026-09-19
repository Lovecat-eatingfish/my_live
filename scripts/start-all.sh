#!/usr/bin/env bash
# Thin shim: real implementation moved to scripts/ops/start-all.sh (2026-09-19 re-org).
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$DIR/ops/start-all.sh" "$@"
