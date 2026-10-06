#!/usr/bin/env bash
# check-tokens.sh    the Swift design tokens are fresh; scripts/ios/test.sh runs it before every build (L18 IO-06a)
#
# - iosApp/Sources/DesignSystem/Tokens.generated.swift must be what scripts/ios/gen-tokens.py makes of
#   shared/designsystem/tokens/itmo-tokens.json (docs/ios.md, Design system). Fix: python3 scripts/ios/gen-tokens.py
# - Exit code: 0 fresh, 1 stale, 2 the tokens are refused (unknown schemaVersion, bad value).

set -u

root=$(cd "$(dirname "$0")/../.." 2> /dev/null && pwd -P) || {
  printf 'check-tokens.sh: cannot resolve the repository root\n' >&2
  exit 1
}

exec /usr/bin/python3 "$root/scripts/ios/gen-tokens.py" --check
