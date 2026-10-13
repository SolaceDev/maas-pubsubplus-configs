#!/usr/bin/env bash
# Deploys a prebuilt artifact and its POM to a Maven repository with the stock
# maven-deploy-plugin. No project build code runs, so the repository
# credentials are never exposed to it.
#
# Usage: MAVEN_USERNAME=... MAVEN_PASSWORD=... publish.sh <file> <pom-file> <repository-url>
# Local dry run: publish.sh build/libs/x.jar pom.xml "file://$PWD/build/test-repo"
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <file> <pom-file> <repository-url>" >&2
  exit 2
fi
file="$1"
pom="$2"
url="$3"

DEPLOY_PLUGIN="org.apache.maven.plugins:maven-deploy-plugin:3.1.4"

settings="$(mktemp)"
trap 'rm -f "$settings"' EXIT
# The credentials stay in the environment; Maven resolves ${env.*} itself.
cat > "$settings" <<'EOF'
<settings>
  <servers>
    <server>
      <id>target</id>
      <username>${env.MAVEN_USERNAME}</username>
      <password>${env.MAVEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
EOF

mvn --batch-mode --no-transfer-progress --strict-checksums --settings "$settings" \
  "${DEPLOY_PLUGIN}:deploy-file" \
  -DrepositoryId=target \
  -Durl="$url" \
  -Dfile="$file" \
  -DpomFile="$pom"
