# Jackson 2 → Jackson 3 Migration — Design

## Problem Summary

maas-pubsubplus-configs (solconfig CLI) depends on `com.fasterxml.jackson.core:jackson-databind:2.18.6` (build.gradle:50). The maas ecosystem is moving to Jackson 3 (`tools.jackson.*`, jackson-bom 3.1.5) as part of the Spring Boot 4 line; maas-core completed this migration under DATAGO-142581. This library must follow so it stays on a supported Jackson line and stays consistent with the ecosystem.

Expected behavior after migration: identical wire output to today. Jackson 3 changes serialization defaults (alphabetical property sorting, enum `toString()` serialization, fail-on-null-primitives, lenient constructor detection) that would silently alter the JSON this tool emits — output that humans diff and commit. Constraint from the user: keep behavior as close to Jackson 2 settings as possible.

Blocker resolved during design: Jackson 3 requires Java 17; build.gradle:20 pins the toolchain to Java 11. Decision: bump to Java 17 (published jar becomes Java 17 bytecode).

## Decisions

- Java toolchain: 11 → 17 (build.gradle:20). Gradle wrapper 7.6 supports 17; CI ubuntu-latest runners have JDK 17, no workflow change forced.
- Dependency: `com.fasterxml.jackson.core:jackson-databind:2.18.6` → `tools.jackson.core:jackson-databind:3.1.5` (build.gradle:50), matching maas-core.
- Chosen approach: centralized `JsonMappers` factory with Jackson 2-compat pins (maas-core pattern). Rejected: inline pins (duplication, drift risk), accepting Jackson 3 defaults (violates output-stability constraint).
- Native image: best-effort. Remove dead `com.fasterxml.jackson.databind.ext.Java7*` entries from reflect-config.json; user regenerates metadata via the `nativeAgent` task and verifies the binary.
- `json-path:2.8.0` untouched: its Jackson usage is internal and Jackson 2/3 have different Maven coordinates and packages, so both can coexist.

## Components

### JsonMappers (new)

`src/main/java/com/solace/tools/solconfig/JsonMappers.java`

- `create()` → pinned `tools.jackson.databind.json.JsonMapper`
- `builder()` → pinned `JsonMapper.Builder` for call sites needing extra config
- Pinning mechanism: `JsonMapper.builderWithJackson2Defaults()` (verified present in jackson-databind 3.1.5). This is Jackson's official Jackson 2 compatibility mode and covers a superset of the maas-core hand-rolled pins: SORT_PROPERTIES_ALPHABETICALLY off, enum toString read/write off, FAIL_ON_NULL_FOR_PRIMITIVES off, FAIL_ON_TRAILING_TOKENS off, DETECT_PARAMETER_NAMES off (Jackson 2 creator semantics), plus date/BigDecimal/getter-as-setter parity. Chosen over hand-rolled pins as strictly closer to Jackson 2. maas-core's extra `ACCEPT_CASE_INSENSITIVE_ENUMS` is deliberately omitted — it is not Jackson 2 behavior.
- API note: `ObjectMapper.writer(PrettyPrinter)` was removed in Jackson 3; `Utils.toPrettyJsonMultiLineArray` uses `writer().with(prettyPrinter)` instead. `writerWithDefaultPrettyPrinter()`, `DefaultPrettyPrinter.indentArraysWith`, and `DefaultIndenter.SYSTEM_LINEFEED_INSTANCE` survive unchanged (verified against 3.1.5 jars).
- API note: `SempClient.readMapFromJsonFile` catches only `IOException`; Jackson 3 parse errors are unchecked `JacksonException` and would escape. Catch broadens to `IOException | JacksonException` to preserve the errPrintlnAndExit behavior.

Contract: every mapper in this codebase is built through this class. Inputs: none. Outputs: configured mapper/builder. Error states: none (pure construction).

### Mechanical renames (6 files)

| File | Change |
|---|---|
| src/main/java/com/solace/tools/solconfig/Utils.java:3-7,24,109-132 | Imports → `tools.jackson.*`; `objectMapper = new ObjectMapper()` → `JsonMappers.create()` (field stays `public static`, callers unchanged); `toPrettyJsonMultiLineArray` local mapper → `JsonMappers.builder().enable(SerializationFeature.INDENT_OUTPUT).build()`; `JsonProcessingException` → `JacksonException` |
| src/main/java/com/solace/tools/solconfig/model/SempMeta.java:3-4 | `JsonProcessingException` → `JacksonException`; `JsonNode` import → `tools.jackson.databind.JsonNode` |
| src/main/java/com/solace/tools/solconfig/model/SempResponse.java:3 | `JsonProcessingException` → `JacksonException` |
| src/main/java/com/solace/tools/solconfig/model/ConfigObject.java:3 | `JsonProcessingException` → `JacksonException` |
| src/main/java/com/solace/tools/solconfig/SempClient.java:275-277 | No import change needed; verify catch blocks still compile (readValue exceptions now unchecked) |
| src/test/java/com/solace/tools/solconfig/model/JsonSpecTest.java:3 | `JsonProcessingException` → `JacksonException` |

`JacksonException` is unchecked; existing catch blocks remain legal and the defensive `errPrintlnAndExit` paths are preserved. `JsonNode` navigation (`get`, `asInt`, `asText`, `readTree`, `treeToValue`) is unchanged in Jackson 3.

### Jackson 2 import guard (new)

`src/test/java/com/solace/tools/solconfig/Jackson2ImportGuardTest.java`

- Scans every `.java` file under `src/main/java` and `src/test/java` for `import com.fasterxml.jackson` (allowing `com.fasterxml.jackson.annotation`, which remains the annotation package in Jackson 3) and fails listing the offending files
- Asserts `com.fasterxml.jackson.databind.ObjectMapper` is not loadable (`Class.forName` throws), proving no Jackson 2 databind artifact is on the classpath transitively

Contract: build fails if Jackson 2 reappears via source imports or via a transitive dependency. Error state: assertion failure naming the offending file or artifact.

### Native image metadata

src/main/resources/META-INF/native-image/solconfig/reflect-config.json: delete the two `com.fasterxml.jackson.databind.ext.Java7HandlersImpl` / `Java7SupportImpl` entries (classes do not exist in Jackson 3). Regeneration of Jackson 3 entries is done by the user via the `nativeAgent` task (build.gradle:13).

## Test Plan

Jackson 2 parity suite (new, `JsonMappersTest`) — one test per pinned default, so any future Jackson 3 default drift fails loudly:
1. Property order: `create()` serializes a POJO in declaration order, not alphabetical (`SORT_PROPERTIES_ALPHABETICALLY` pin)
2. Enum write: enum serializes via `name()`, not `toString()` — verified with an enum whose `toString()` differs from `name()` (`WRITE_ENUMS_USING_TO_STRING` pin)
3. Enum read: `name()` string deserializes to the enum; `toString()` form is rejected (`READ_ENUMS_USING_TO_STRING` pin)
4. Null primitives: null deserializes onto a primitive field as its default, no throw (`FAIL_ON_NULL_FOR_PRIMITIVES` pin)
5. Creator semantics: a POJO with a non-annotated multi-arg constructor is not implicitly bound; binding follows Jackson 2 explicit-only rules (constructor detector pin)
6. Map order: `LinkedHashMap` insertion order preserved in output
7. Errors: malformed JSON throws `JacksonException`

Guard tests (new, `Jackson2ImportGuardTest`):
8. No `import com.fasterxml.jackson` outside `.annotation` anywhere under `src/`
9. `Class.forName("com.fasterxml.jackson.databind.ObjectMapper")` throws — Jackson 2 databind absent from classpath

Integration/regression:
10. Existing suite (`JsonSpecTest`, `SempSpecTest`, others) passes unmodified — proves wire output did not drift
11. `Utils.toPrettyJson` / `toPrettyJsonMultiLineArray` output matches current formatting (multi-line arrays, system linefeed indent)
12. `./gradlew build` produces the fat jar under toolchain 17

Test output saved to file for review. Verification the user must do: regenerate native-image metadata (`nativeAgent`) and smoke-test the native binary; confirm no downstream consumer of the published jar requires Java 11.

## Failure Modes

- CI runner lacks JDK 17 → Gradle toolchain error, fails fast. Permanent; fix by adding setup-java to workflows. Blast radius: CI only.
- Downstream consumer on Java ≤ 16 loads the published jar → `UnsupportedClassVersionError`. Permanent; assumption accepted by user. If wrong, plan is invalidated — escalate.
- Native binary missing Jackson 3 reflection entries at runtime → degraded, known, user regenerates metadata.
- Jackson 3 default drift not covered by pins → caught by parity tests 1-7; escalate any new drift and pin it in `JsonMappers`.
- Jackson 2 reintroduced by a future dependency or import → caught by guard tests 8-9 at build time.

## Assumptions

- No downstream consumer of the GitHub Packages jar requires Java 11.
- jackson-databind 3.1.5 is available on Maven Central under `tools.jackson.core`.
- No enum types in this codebase are serialized today (pins added anyway for safety).

## Questions

- None open; Java target (17) and native-image scope (best-effort) were decided during design.
