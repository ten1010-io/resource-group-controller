#!/usr/bin/env bash
script_path=$(dirname "$0")

output_dir_path="$script_path/output"
if [ ! -e "$output_dir_path" ]; then
  mkdir "$output_dir_path"
fi
if [ ! -d "$output_dir_path" ]; then
  echo "Error : Provided output directory path was not directory"
  exit 1
fi
if [ -e "$output_dir_path/ca.crt" ]; then
  echo "Error : File [$output_dir_path/ca.crt] already exist"
  exit 1
fi

openssl req -config "${script_path}/ca.conf" -newkey rsa -x509 -days 3650 -keyout "${output_dir_path}/ca.key" -out "${output_dir_path}/ca.crt" -set_serial 0
