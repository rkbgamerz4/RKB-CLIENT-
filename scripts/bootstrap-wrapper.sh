#!/usr/bin/env bash
# Generates gradlew, gradlew.bat and gradle-wrapper.jar (binary files that could not be
# shipped inside this ZIP). Run once, then use ./gradlew from then on.
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v gradle >/dev/null 2>&1; then
  if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    # shellcheck disable=SC1091
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install gradle
  else
    echo "Gradle not found. Install it (Codespaces: 'sdk install gradle') and re-run." >&2
    exit 1
  fi
fi
gradle wrapper --gradle-version 9.2.0
chmod +x gradlew
echo "Wrapper generated. Next: ./gradlew build"
