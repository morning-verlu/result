#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS_SCRIPT="${SCRIPT_DIR}/build-docs.ps1"

if [[ ! -f "${PS_SCRIPT}" ]]; then
  echo "build-docs.ps1 not found: ${PS_SCRIPT}"
  exit 1
fi

powershell -NoProfile -ExecutionPolicy Bypass -File "${PS_SCRIPT}" "$@"
