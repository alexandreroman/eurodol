#!/bin/sh
set -e -x
VERSION=`cat version/number`
mkdir -p build
cd repo
./mvnw versions:set -DnewVersion=$VERSION && \
./mvnw -Dmaven.repo.local=../.m2 -B package && cp target/*.jar ../build/$APP-$VERSION.jar && \
echo "$APP $VERSION" > ../build/release && \
cat > ../build/manifest.yml << EOF
---
applications:
  - name: $APP
    path: $APP-$VERSION.jar
    health-check-type: http
    health-check-http-endpoint: /actuator/health
EOF
