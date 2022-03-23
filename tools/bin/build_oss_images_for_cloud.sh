#!/usr/bin/env bash

set -e


if test -z "$BRANCH"; then
  BRANCH=$(git rev-parse --abbrev-ref HEAD)
fi

VERSION=oss-branch_$(git rev-parse --short "$BRANCH")

echo "Using VERSION=$VERSION"

# set build flags so M1 can build kube-compatible amd64 images
SUB_BUILD=PLATFORM \
VERSION="${VERSION}" \
ALPINE_IMAGE=alpine:3.14 \
POSTGRES_IMAGE=postgres:13-alpine \
DOCKER_BUILD_PLATFORM=linux/amd64 \
DOCKER_BUILD_ARCH=amd64 \
./gradlew clean build -x test

echo "Publishing to Maven Local for Cloud build"
VERSION="${VERSION}" SUB_BUILD=PLATFORM ./gradlew publishToMavenLocal

