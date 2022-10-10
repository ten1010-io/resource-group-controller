#!/usr/bin/env bash
script_path=$(dirname "$0")

output_dir_path="$script_path/output"
if [ ! -f "$output_dir_path/ca.crt" ]; then
  echo "Error : File [$output_dir_path/ca.crt] not exist"
  exit 1
fi
cat "$output_dir_path/ca.crt" | base64 -w 0
echo ""
