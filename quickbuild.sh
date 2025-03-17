#!/bin/bash
set -e -o pipefail -u
mvn install --no-snapshot-updates -DskipTests -T4
