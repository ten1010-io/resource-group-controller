#!/usr/bin/env bash
script_path=$(dirname "$0")

${script_path}/mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=resource-group-controller:1.1.0-SNAPSHOT
