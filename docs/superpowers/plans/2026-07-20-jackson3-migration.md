# Jackson 2 → Jackson 3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate solconfig from `com.fasterxml.jackson.core:jackson-databind:2.18.6` to `tools.jackson.core:jackson-databind:3.1.5` with output identical to Jackson 2.

**Architecture:** Jackson 2 and Jackson 3 have different Maven coordinates and packages, so both coexist during migration: add Jackson 3 + a pinned `JsonMappers` factory first (green), migrate the six source files (green), then remove Jackson 2 with a guard test that keeps it out permanently.

**Tech Stack:** Java 17 toolchain, Gradle 7.6, `tools.jackson.core:jackson-databind:3.1.5`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-07-20-jackson3-migration-design.md`

## Global Constraints

- Java toolchain: exactly 17 (`JavaLanguageVersion.of(17)`)
- Jackson 3: exactly `tools.jackson.core:jackson-databind:3.1.5`
- All mappers built via `JsonMappers` (which uses `JsonMapper.builderWithJackson2Defaults()`); never `new ObjectMapper()` or bare `JsonMapper.builder()`
- Do NOT enable `ACCEPT_CASE_INSENSITIVE_ENUMS` (not Jackson 2 behavior)
- No `import com.fasterxml.jackson.*` anywhere except `com.fasterxml.jackson.annotation.*`
- Zero code comments; no Co-authored-by trailers in commits
- Save test output to a file when running tests: `./gradlew test 2>&1 | tee /tmp/solconfig-test.log`
- Working branch: `jclarke/jackson3-migration`

---

### Task 1: Java 17 toolchain + Jackson 3 dependency (coexisting with Jackson 2)

**Files:**
- Modify: `build.gradle:20` (toolchain), `build.gradle:50` (dependencies block)

**Interfaces:**
- Produces: compile+test classpath containing both `com.fasterxml.jackson.core:jackson-databind:2.18.6` and `tools.jackson.core:jackson-databind:3.1.5`; Java 17 toolchain (Jackson 3 requires 17)

- [ ] **Step 1: Verify the current build is green before touching anything**

Run: `./gradlew clean test 2>&1 | tee /tmp/solconfig-test-baseline.log`
Expected: BUILD SUCCESSFUL. If not, STOP and report — the migration must start from green.

- [ ] **Step 2: Bump toolchain and add Jackson 3 dependency**

In `build.gradle`, change line 20:

```groovy
        languageVersion = JavaLanguageVersion.of(17)
```

In the `dependencies` block, directly below the existing line `implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.6'`, add:

```groovy
    implementation 'tools.jackson.core:jackson-databind:3.1.5'
```

Do NOT remove the Jackson 2 line yet (Task 4 does that behind a guard test).

Note: Jackson 3 transitively pulls `com.fasterxml.jackson.core:jackson-annotations` 2.21+ (annotations deliberately stayed on the Jackson 2 groupId). This is expected — do not exclude it; databind 3.x fails at runtime without it (`NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonSerializeAs`).

- [ ] **Step 3: Verify build and tests still pass on Java 17 with both Jacksons**

Run: `./gradlew clean test 2>&1 | tee /tmp/solconfig-test-task1.log`
Expected: BUILD SUCCESSFUL, same test count as baseline. A toolchain error like "No matching toolchains" means no JDK 17 is installed — ask the user to install one (e.g. Temurin 17) rather than working around it.

- [ ] **Step 4: Commit**

```bash
git add build.gradle
git commit -m "build: Java 17 toolchain, add Jackson 3 alongside Jackson 2"
```

---

### Task 2: JsonMappers factory + Jackson 2 parity test suite

**Files:**
- Create: `src/main/java/com/solace/tools/solconfig/JsonMappers.java`
- Test: `src/test/java/com/solace/tools/solconfig/JsonMappersTest.java`

**Interfaces:**
- Produces: `JsonMappers.create()` → `tools.jackson.databind.json.JsonMapper` (pinned); `JsonMappers.builder()` → `tools.jackson.databind.json.JsonMapper.Builder` (pinned). Task 3 uses both.

- [ ] **Step 1: Write the failing parity tests**

Create `src/test/java/com/solace/tools/solconfig/JsonMappersTest.java`:

```java
package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonMappersTest {
    private final JsonMapper mapper = JsonMappers.create();

    static class DeclarationOrderPojo {
        public int zebra = 1;
        public int apple = 2;
        public int mango = 3;
    }

    enum WireEnum {
        FIRST_VALUE;

        @Override
        public String toString() {
            return "first-value";
        }
    }

    static class PrimitiveHolder {
        public int count = 42;
    }

    @Test
    void serializesPropertiesInDeclarationOrderNotAlphabetical() {
        assertEquals("{\"zebra\":1,\"apple\":2,\"mango\":3}",
                mapper.writeValueAsString(new DeclarationOrderPojo()));
    }

    @Test
    void writesEnumsByNameNotToString() {
        assertEquals("\"FIRST_VALUE\"", mapper.writeValueAsString(WireEnum.FIRST_VALUE));
    }

    @Test
    void readsEnumsByNameAndRejectsToStringForm() {
        assertEquals(WireEnum.FIRST_VALUE, mapper.readValue("\"FIRST_VALUE\"", WireEnum.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("\"first-value\"", WireEnum.class));
    }

    @Test
    void deserializesNullOntoPrimitiveAsDefault() {
        assertEquals(0, mapper.readValue("{\"count\":null}", PrimitiveHolder.class).count);
    }

    @Test
    void pinsJackson2CreatorAndFeatureDefaults() {
        assertFalse(mapper.isEnabled(MapperFeature.DETECT_PARAMETER_NAMES));
        assertFalse(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        assertFalse(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS));
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertFalse(mapper.isEnabled(EnumFeature.READ_ENUMS_USING_TO_STRING));
        assertFalse(mapper.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING));
    }

    @Test
    void preservesMapInsertionOrder() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("zebra", 1);
        map.put("apple", 2);
        assertEquals("{\"zebra\":1,\"apple\":2}", mapper.writeValueAsString(map));
    }

    @Test
    void throwsJacksonExceptionOnMalformedJson() {
        assertThrows(JacksonException.class, () -> mapper.readTree("{invalid"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.solace.tools.solconfig.JsonMappersTest' 2>&1 | tee /tmp/solconfig-test-task2-red.log`
Expected: FAIL to compile — `JsonMappers` does not exist ("cannot find symbol: class JsonMappers").

- [ ] **Step 3: Write the factory**

Create `src/main/java/com/solace/tools/solconfig/JsonMappers.java`:

```java
package com.solace.tools.solconfig;

import tools.jackson.databind.json.JsonMapper;

public final class JsonMappers {

    private JsonMappers() {
    }

    public static JsonMapper create() {
        return builder().build();
    }

    public static JsonMapper.Builder builder() {
        return JsonMapper.builderWithJackson2Defaults();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.solace.tools.solconfig.JsonMappersTest' 2>&1 | tee /tmp/solconfig-test-task2-green.log`
Expected: PASS, 7 tests. If any parity assertion fails, `builderWithJackson2Defaults()` does not restore that Jackson 2 default on 3.1.5 — add the corresponding explicit `.disable(...)` to `JsonMappers.builder()` and note it in the commit message; do not weaken the test.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/solace/tools/solconfig/JsonMappers.java src/test/java/com/solace/tools/solconfig/JsonMappersTest.java
git commit -m "feat: JsonMappers factory pinned to Jackson 2 defaults with parity tests"
```

---

### Task 3: Migrate the six source files to Jackson 3

**Files:**
- Modify: `src/main/java/com/solace/tools/solconfig/Utils.java:3-7,24,109-132`
- Modify: `src/main/java/com/solace/tools/solconfig/model/SempMeta.java:3-4,86`
- Modify: `src/main/java/com/solace/tools/solconfig/model/SempResponse.java:3,29`
- Modify: `src/main/java/com/solace/tools/solconfig/model/ConfigObject.java:3,108,128`
- Modify: `src/main/java/com/solace/tools/solconfig/SempClient.java:272-285`
- Modify: `src/test/java/com/solace/tools/solconfig/model/JsonSpecTest.java:3,126`

**Interfaces:**
- Consumes: `JsonMappers.create()` and `JsonMappers.builder()` from Task 2
- Produces: `Utils.objectMapper` retyped from `com.fasterxml.jackson.databind.ObjectMapper` to `tools.jackson.databind.json.JsonMapper` (still `public static`, same call sites: `readTree`, `treeToValue`, `readValue`, `writeValueAsString`); `UtilsPrettyJsonTest` characterization tests

- [ ] **Step 1: Write pretty-print characterization tests (against the CURRENT Jackson 2 code)**

These capture today's output format so the migration provably doesn't change it. Expected strings below were verified against both Jackson 2 defaults and Jackson 3.1.5 `builderWithJackson2Defaults()`.

Create `src/test/java/com/solace/tools/solconfig/UtilsPrettyJsonTest.java`:

```java
package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsPrettyJsonTest {

    @Test
    void prettyJsonKeepsJackson2Format() {
        assertEquals("{\n  \"name\" : \"q1\",\n  \"owners\" : [ \"a\", \"b\" ]\n}",
                normalize(Utils.toPrettyJson(sample())));
    }

    @Test
    void prettyJsonMultiLineArrayKeepsJackson2Format() {
        assertEquals("{\n  \"name\" : \"q1\",\n  \"owners\" : [\n    \"a\",\n    \"b\"\n  ]\n}",
                normalize(Utils.toPrettyJsonMultiLineArray(sample())));
    }

    private Map<String, Object> sample() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "q1");
        map.put("owners", List.of("a", "b"));
        return map;
    }

    private String normalize(String json) {
        return json.replace(System.lineSeparator(), "\n");
    }
}
```

- [ ] **Step 2: Run characterization tests — must pass BEFORE migrating**

Run: `./gradlew test --tests 'com.solace.tools.solconfig.UtilsPrettyJsonTest' 2>&1 | tee /tmp/solconfig-test-task3-char.log`
Expected: PASS (2 tests) against the existing Jackson 2 implementation. If an assertion fails, the expected string does not match real Jackson 2 output — correct the expected string to the actual output (this step defines the baseline), then continue.

- [ ] **Step 3: Commit the characterization tests**

```bash
git add src/test/java/com/solace/tools/solconfig/UtilsPrettyJsonTest.java
git commit -m "test: characterize pretty-print output format before Jackson 3 migration"
```

- [ ] **Step 4: Migrate Utils.java**

Replace the imports at `Utils.java:3-7`:

```java
import tools.jackson.core.JacksonException;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
```

Replace line 24:

```java
    public static JsonMapper objectMapper = JsonMappers.create();
```

Replace `toPrettyJson` (lines 109-117):

```java
    public static String toPrettyJson(Object obj) {
        String result = null;
        try {
            result = Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JacksonException e) {
            errPrintlnAndExit(e, "Unable to convert the object into the json format.");
        }
        return result;
    }
```

Replace `toPrettyJsonMultiLineArray` (lines 119-132) — note `writer(prettyPrinter)` was removed in Jackson 3, replaced by `writer().with(prettyPrinter)`:

```java
    public static String toPrettyJsonMultiLineArray(Object obj) {
        String result = null;
        try {
            DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
            prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
            result = JsonMappers.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build()
                    .writer()
                    .with(prettyPrinter)
                    .writeValueAsString(obj);
        } catch (JacksonException e) {
            errPrintlnAndExit(e, "Unable to convert the object into the json format.");
        }
        return result;
    }
```

- [ ] **Step 5: Migrate the three model classes**

`SempMeta.java` — replace imports at lines 3-4:

```java
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
```

and at line 86 change `} catch (JsonProcessingException e) {` to:

```java
        } catch (JacksonException e) {
```

`SempResponse.java` — replace the import at line 3 with `import tools.jackson.core.JacksonException;` and at line 29 change `} catch (JsonProcessingException e) {` to `} catch (JacksonException e) {`.

`ConfigObject.java` — replace the import at line 3 with `import tools.jackson.core.JacksonException;` and change both catch clauses (lines 108 and 128) from `JsonProcessingException` to `JacksonException`.

- [ ] **Step 6: Migrate SempClient.readMapFromJsonFile**

Jackson 3 parse errors are unchecked `JacksonException` and no longer satisfy `catch (IOException)` — without this change a malformed config file would bypass `errPrintlnAndExit`. Add the import `import tools.jackson.core.JacksonException;` to `SempClient.java` and change line 279 from `} catch (IOException e) {` to:

```java
        } catch (IOException | JacksonException e) {
```

- [ ] **Step 7: Migrate JsonSpecTest**

Delete line 3 (`import com.fasterxml.jackson.core.JsonProcessingException;`) and at line 126 remove the now-meaningless checked-throws clause, changing

```java
    void testFindSpecialAttributes(String path, String expected) throws JsonProcessingException {
```

to:

```java
    void testFindSpecialAttributes(String path, String expected) {
```

- [ ] **Step 8: Run the full suite — wire-compat regression gate**

Run: `./gradlew clean test 2>&1 | tee /tmp/solconfig-test-task3.log`
Expected: BUILD SUCCESSFUL, same test count as the Task 1 baseline plus the 7 JsonMappersTest and 2 UtilsPrettyJsonTest tests. Any pre-existing or characterization test that fails here is Jackson 3 behavior drift — fix by adding the missing pin to `JsonMappers`, never by changing the test's expected output.

- [ ] **Step 9: Verify no stray Jackson 2 imports remain**

Run: `grep -rn 'import com.fasterxml.jackson' src/`
Expected: no output.

- [ ] **Step 10: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: migrate solconfig from Jackson 2 to Jackson 3 via JsonMappers"
```

---

### Task 4: Jackson 2 import guard + remove Jackson 2 dependency

**Files:**
- Create: `src/test/java/com/solace/tools/solconfig/Jackson2ImportGuardTest.java`
- Modify: `build.gradle` (remove the Jackson 2 dependency line)

**Interfaces:**
- Consumes: nothing from earlier tasks (self-contained guard)
- Produces: build fails if Jackson 2 reappears as an import or classpath artifact

- [ ] **Step 1: Write the guard test**

Create `src/test/java/com/solace/tools/solconfig/Jackson2ImportGuardTest.java` (Gradle runs tests with the project root as working directory, so `Path.of("src")` resolves correctly):

```java
package com.solace.tools.solconfig;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Jackson2ImportGuardTest {

    @Test
    void noJackson2ImportsInSources() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src"))) {
            List<String> offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::hasJackson2Import)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            assertEquals(List.of(), offenders);
        }
    }

    @Test
    void jackson2DatabindAbsentFromClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"));
    }

    private boolean hasJackson2Import(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .anyMatch(line -> line.startsWith("import com.fasterxml.jackson")
                            && !line.startsWith("import com.fasterxml.jackson.annotation"));
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
```

- [ ] **Step 2: Run guard test to verify the classpath check fails**

Run: `./gradlew test --tests 'com.solace.tools.solconfig.Jackson2ImportGuardTest' 2>&1 | tee /tmp/solconfig-test-task4-red.log`
Expected: `noJackson2ImportsInSources` PASSES (Task 3 removed all imports); `jackson2DatabindAbsentFromClasspath` FAILS — Jackson 2 is still a declared dependency, so `Class.forName` succeeds. This proves the guard detects classpath leaks.

- [ ] **Step 3: Remove the Jackson 2 dependency**

In `build.gradle`, delete the line:

```groovy
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.6'
```

- [ ] **Step 4: Run the full suite to verify everything passes**

Run: `./gradlew clean test 2>&1 | tee /tmp/solconfig-test-task4-green.log`
Expected: BUILD SUCCESSFUL, all tests pass including both guard tests. If compilation fails here, a source file still references Jackson 2 — fix the import (migrate it to `tools.jackson`), do not re-add the dependency.

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/test/java/com/solace/tools/solconfig/Jackson2ImportGuardTest.java
git commit -m "feat: remove Jackson 2 dependency, guard against reintroduction"
```

---

### Task 5: Native-image metadata cleanup + final verification

**Files:**
- Modify: `src/main/resources/META-INF/native-image/solconfig/reflect-config.json` (remove two entries)

**Interfaces:**
- Consumes: nothing; independent cleanup
- Produces: reflect-config.json free of Jackson 2 class references; regeneration of Jackson 3 entries is deferred to the user (`./gradlew nativeAgent` + native binary smoke test) per the spec's best-effort decision

- [ ] **Step 1: Remove the two dead Jackson 2 entries**

In `src/main/resources/META-INF/native-image/solconfig/reflect-config.json`, delete these two JSON objects (they reference classes that do not exist in Jackson 3; keep the surrounding array valid — watch trailing commas):

```json
{
  "name":"com.fasterxml.jackson.databind.ext.Java7HandlersImpl",
  "methods":[{"name":"<init>","parameterTypes":[] }]
},
{
  "name":"com.fasterxml.jackson.databind.ext.Java7SupportImpl",
  "methods":[{"name":"<init>","parameterTypes":[] }]
},
```

- [ ] **Step 2: Validate the JSON and confirm no Jackson references remain in native-image configs**

Run: `python3 -m json.tool src/main/resources/META-INF/native-image/solconfig/reflect-config.json > /dev/null && grep -c 'jackson' src/main/resources/META-INF/native-image/solconfig/*.json`
Expected: json.tool exits 0 (valid JSON); grep reports 0 for every file (grep exits 1 when nothing matches — that is the expected outcome).

- [ ] **Step 3: Full clean build including the fat jar**

Run: `./gradlew clean build 2>&1 | tee /tmp/solconfig-build-final.log`
Expected: BUILD SUCCESSFUL; `build/libs/solconfig.jar` exists.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/META-INF/native-image/solconfig/reflect-config.json
git commit -m "chore: drop Jackson 2 reflect-config entries absent from Jackson 3"
```

- [ ] **Step 5: Hand off user verification items**

Report to the user (do not attempt these yourself):
1. Regenerate native-image metadata: build the jar, then run `./gradlew nativeAgent`, and smoke-test the native binary (`solconfig test` against a broker).
2. Smoke-test the jar CLI end-to-end against a real broker: run `java -jar build/libs/solconfig.jar backup` on the same broker with the pre-migration and post-migration jars and diff the two emitted config files — they must be byte-identical.
3. Confirm no downstream consumer of the GitHub Packages artifact requires Java 11 (the jar is now Java 17 bytecode).
