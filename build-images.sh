#!/usr/bin/env bash
script_path=$(dirname "$0")

docker build -t ten1010io/resource-group-controller:1.0.2-SNAPSHOT ${script_path}
