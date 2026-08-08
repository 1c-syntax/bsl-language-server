#!/bin/bash
# Run a command, retrying it on failure.
# Usage: retry.sh <attempts> <delay-seconds> <command> [args...]
#
# Intended for steps that depend on external resources (javadoc downloads element-list
# from every -link target, including javadoc.io, which is periodically unavailable).

set -uo pipefail

ATTEMPTS="$1"
DELAY="$2"
shift 2

for attempt in $(seq 1 "$ATTEMPTS"); do
  if "$@"; then
    exit 0
  fi

  if [ "$attempt" -lt "$ATTEMPTS" ]; then
    echo "::warning::Attempt $attempt of $ATTEMPTS failed: $*. Retrying in ${DELAY}s."
    sleep "$DELAY"
  fi
done

echo "::error::All $ATTEMPTS attempts failed: $*"
exit 1
