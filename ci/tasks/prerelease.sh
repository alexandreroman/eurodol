#!/bin/sh
set -e -x
VERSION=`cat version/number`
mkdir -p prerelease
cp build/$APP-$VERSION.jar prerelease
echo "$APP $VERSION" > prerelease/release && \
cat > prerelease/manifest.yml << EOF
---
applications:
  - name: $APP
    path: $APP-$VERSION.jar
    health-check-type: http
    health-check-http-endpoint: /actuator/health
EOF
