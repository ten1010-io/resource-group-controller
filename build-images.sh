#!/usr/bin/env bash
script_path=$(dirname "$0")

${script_path}/mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=ten1010io/resource-group-controller:1.0.0
