#!/usr/bin/env bash
script_path=$(dirname "$0")

output_dir_path="$script_path/output"
if [ ! -d "$output_dir_path" ]; then
  echo "Error : Provided pki directory path was not directory"
  exit 1
fi
if [ ! -f "$output_dir_path/ca.crt" ]; then
  echo "Error : File [$output_dir_path/ca.crt] not exist"
  exit 1
fi
if [ -e "$output_dir_path/tls.crt" ]; then
  echo "Error : File [$output_dir_path/tls.crt] already exist"
  exit 1
fi

openssl req -newkey rsa -config "${script_path}/tls.conf" -keyout "${output_dir_path}/tls.key" -out "${output_dir_path}/tls.csr"
openssl x509 -req -CA "${output_dir_path}/ca.crt" -CAkey "${output_dir_path}/ca.key" -CAserial "${output_dir_path}/.srl" -CAcreateserial -in "${output_dir_path}/tls.csr" -extfile "${script_path}/tls.ext" -out "${output_dir_path}/tls.crt" -days 365
openssl pkcs12 -export -inkey "${output_dir_path}/tls.key" -in "${output_dir_path}/tls.crt" -out "${output_dir_path}/tls.p12" -passout pass:""
