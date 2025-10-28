#!/bin/bash
# Check if a pull request exists for the current branch
# Returns: Outputs "true" if PR exists for non-protected branches, "false" otherwise

set -e

EVENT_NAME="$1"
REF_NAME="$2"
REPOSITORY="$3"

if [ "$EVENT_NAME" == "push" ]; then
  # Always run workflows for master and develop branches
  if [ "$REF_NAME" == "master" ] || [ "$REF_NAME" == "develop" ]; then
    echo "false"
  else
    # Check if PR exists for this branch
    prs=$(gh pr list --repo "$REPOSITORY" --head "$REF_NAME" --state open --json number --jq 'length')
    if [ "$prs" -gt 0 ]; then
      echo "true"
    else
      echo "false"
    fi
  fi
else
  echo "false"
fi
