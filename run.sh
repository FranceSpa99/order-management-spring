#!/bin/bash
set -e
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
