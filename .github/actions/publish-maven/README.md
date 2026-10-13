# Publish Maven artifact

Deploys a prebuilt artifact and its POM to a Maven repository with the stock
`maven-deploy-plugin` (`deploy:deploy-file`).

It exists so publishing never runs the project's own build. `build.yaml`
builds and tests with no secrets and uploads the jar and POM as an artifact;
a separate job in the `release` environment downloads them and runs this
action. Build scripts, Gradle plugins and test code therefore never execute
while the package credentials are present.

Maven and a JDK must be on the runner (both are on `ubuntu-latest`).

## Inputs

| Input | Required | Description |
|---|---|---|
| `file` | yes | Artifact to deploy, e.g. the jar. |
| `pom-file` | yes | POM for the artifact. Group, artifact id and version are read from it. |
| `repository-url` | yes | Maven repository URL. |
| `username` | yes | Repository username. |
| `password` | yes | Repository password or token. |

## Outputs

None.

## Usage

```yaml
publish:
  needs: build
  runs-on: ubuntu-latest
  environment: release
  permissions:
    contents: read
  steps:
    - uses: actions/checkout@<sha> # vX
      with:
        persist-credentials: false
        sparse-checkout: .github/actions/publish-maven
    - uses: actions/download-artifact@<sha> # vX
      with:
        name: maven-publication
        path: publication
    - uses: ./.github/actions/publish-maven
      with:
        file: publication/libs/maas-pubsubplus-configs.jar
        pom-file: publication/publications/jar/pom-default.xml
        repository-url: https://maven.pkg.github.com/SolaceDev/maas-maven-artifacts
        username: ${{ secrets.PACKAGES_ADMIN_USER }}
        password: ${{ secrets.PACKAGES_ADMIN_TOKEN }}
```

## Running locally

```sh
./gradlew -Pversion=1.2.3 build generatePomFileForJarPublication
.github/actions/publish-maven/publish.sh \
  build/libs/maas-pubsubplus-configs.jar \
  build/publications/jar/pom-default.xml \
  "file://$PWD/build/test-repo"
```

For a real repository, set `MAVEN_USERNAME` and `MAVEN_PASSWORD`.
