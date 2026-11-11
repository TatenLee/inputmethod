#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
if [ -d "$project_root/.toolchain/jdk" ]; then
  export JAVA_HOME="$project_root/.toolchain/jdk"
fi
if [ -d "$project_root/.toolchain/android-sdk" ]; then
  export ANDROID_HOME="$project_root/.toolchain/android-sdk"
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
python3 scripts/prepare.py --assets-only
if [ "$#" -eq 0 ]; then
  set -- :core:test :app:assembleDebug :app:lintDebug
fi
exec python3 scripts/run-gradle.py "$@"
