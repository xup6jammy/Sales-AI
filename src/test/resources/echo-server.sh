#!/usr/bin/env sh
while IFS= read -r line; do
  printf '%s\n' "$line"
done
