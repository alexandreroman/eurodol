#!/bin/sh
set -e -x
VERSION=`cat version/number`
mkdir -p build
cd repo
./mvnw versions:set -DnewVersion=$VERSION && \
./mvnw -Dmaven.repo.local=../.m2 -B package && cp target/*.jar ../build/$APP-$VERSION.jar && \
echo "$APP $VERSION" > ../build/release && \
cp manifest.yml ../build
