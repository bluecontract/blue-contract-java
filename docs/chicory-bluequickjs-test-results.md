# Chicory blue-quickjs test results

This file records proof commands and outcomes for the Chicory blue-quickjs
runtime branch.

## Environment

- Date: 2026-05-12
- Java repo branch: `feature/chicory-bluequickjs-wasm-runtime`
- Java runtime: OpenJDK 21.0.10
- Gradle wrapper: 8.4
- blue-quickjs local checkout: `/tmp/blue-quickjs`
- blue-quickjs commit: `d462a11818049d7909bbe3ceb36bddd2b532e9cd`
- blue-quickjs QuickJS submodule:
  `9d1eda6e0d1ec36c279d87380db77fbcc3acbae8`

## Baseline Node bridge

Command:

```bash
./gradlew clean test -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- `BUILD SUCCESSFUL in 33s`
- 6 actionable tasks executed.

Representative output:

```text
QuickJS counter snapshot round-trip stress: iterations=100,
totalGas=18700, minGas=187, maxGas=187,
finalBlueId=qQgDoUkPVc2QPWEJar82QSy2kUahX8HHZJHDWivHmEM

No-JS counter snapshot round-trip stress: iterations=100,
totalGas=18100, minGas=181, maxGas=181,
finalBlueId=9kr8UvMAUAZ2wdk3EreY2ShtC5Q8927uaBdn9QTxqxyj
```

## Pending proof commands

These commands have now been run for the initial optional module skeleton:

```bash
./gradlew :quickjs-chicory:dependencies --configuration runtimeClasspath
```

Outcome:

- Passed.
- Runtime classpath contains `com.dylibso.chicory:runtime:1.7.5` and
  `com.dylibso.chicory:wasm:1.7.5`.
- No Node, V8, Javet, QuickJs4J, Wasmtime, or JNI dependency appeared in the
  Chicory module runtime classpath.

```bash
./gradlew clean test -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed after adding the empty optional `quickjs-chicory` module.
- Existing root tests still pass with the Node bridge baseline.

## Core runtime injection layer

Command:

```bash
./gradlew test --tests 'blue.contract.processor.conversation.SequentialWorkflowExecutionTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Added coverage proved that `BlueDocumentProcessorOptions` can inject a
  `JavaScriptRuntime`, and that `ConversationProcessors.registerWith` can inject
  a custom `SequentialWorkflowRunner`.

Command:

```bash
./gradlew clean test -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed after the core injection API was added.
- Existing default processor registration behavior remains covered and green.

## Resource and Host.v1 integrity

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*BlueQuickJsResourceIntegrityTest' \
  --tests '*HostV1ManifestTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Verified canonical wasm resource presence, magic bytes, metadata hash, pinned
  engine hash, Host.v1 hash, gas/profile pins, approved import set, and required
  exports.
- Verified an incorrect expected engine hash fails closed before evaluation.

## Deterministic DV codec

Command:

```bash
./gradlew :quickjs-chicory:test --tests '*DeterministicValueCodecTest'
```

Outcome:

- Passed.
- Golden encodings matched the blue-quickjs DV documentation.
- Allowed values round-tripped.
- Rejection cases covered NaN, infinities, negative zero, duplicate/unsorted map
  keys, indefinite lengths, tags, half/float32, overlong strings, oversized
  encoded values, excessive depth, arrays, and maps.

Command:

```bash
./gradlew :quickjs-chicory:test -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Full current `quickjs-chicory` test set is green.

## Source wrapper and result parser

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*BlueQuickJsSourceWrapperTest' \
  --tests '*BlueQuickJsResultParserTest'
```

Outcome:

- Passed.
- Verified expression, block, and raw source wrapping matches `evaluate.mjs`.
- Verified VM `RESULT` / `ERROR` output parsing with gas trailers and malformed
  output rejection.

Command:

```bash
./gradlew :quickjs-chicory:test -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Full current `quickjs-chicory` test set remains green.

## Host.v1 dispatcher

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryDocumentHostTest' \
  --tests '*ChicoryHostCallAbiTest'
```

Outcome:

- Passed.
- Covered `document.get`, `document.getCanonical`, metadata override behavior,
  JSON Pointer escaping, missing values, emit limit envelope behavior, malformed
  requests, oversized request/response limits, unknown function IDs, reentrant
  calls, and internal failure containment.

Command:

```bash
./gradlew :quickjs-chicory:test -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Full current `quickjs-chicory` test set remains green after Host.v1
  dispatcher additions.

## Chicory runtime smoke

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryBlueQuickJsRuntimeSmokeTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Executed the canonical blue-quickjs wasm32 release artifact through Chicory.
- Repeated each smoke expression 100 times and verified no value, wasm gas, or
  host gas drift.
- Covered arithmetic, string concatenation, object/array return, and array map.

## Forbidden surface and OOG boundaries

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryForbiddenSurfaceTest' \
  --tests '*ChicoryOutOfGasTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Forbidden surface parity covered:
  - `typeof Date`
  - `typeof process`
  - `typeof require`
  - `Math.random()`
  - `eval("1")`
  - `Function("return 1")()`
  - `new Proxy({}, {})`
  - `typeof WeakRef`
- OOG parity/repetition covered:
  - host gas limit `0`
  - host gas limit `1`
  - `while (true) {}`
  - large loop
  - recursive function
  - `document.get` loop
- Each OOG case was repeated 5 times and checked for no Chicory result drift.

## Chicory vs Node parity

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryVsNodeParityTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed after normalizing the raw Chicory OOG error to the same deterministic
  category/message exposed by the Node bridge.
- Generated parity report:
  `quickjs-chicory/build/reports/blue-quickjs-chicory-parity.json`
- Covered:
  - simple arithmetic expression
  - event binding expression
  - currentContract expression
  - steps binding expression
  - document simple value
  - document canonical value
  - document metadata lookup
  - array map/reduce
  - JSON.stringify deterministic key ordering
  - block mode object result
  - block mode array result
  - forbidden global
  - out-of-gas loop

Report status: `passed`; mismatches: `[]`.

## Chicory workflow injection

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicorySequentialWorkflowExecutionTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Verified `SequentialWorkflowRunner.withJavaScriptRuntime(new
  ChicoryBlueQuickJsRuntime(...))` can be injected through
  `BlueDocumentProcessorOptions` and used by real workflow execution.

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryCounterSnapshotRoundTripStressTest' \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Ran 100 Chicory-backed counter workflow iterations through canonical snapshot
  round trips.
- Verified counter progression, snapshot/BlueId preservation, positive gas, and
  stable gas for equivalent operations.

Command:

```bash
./gradlew :quickjs-chicory:test \
  -PblueQuickJsRoot=/tmp/blue-quickjs \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Full current `quickjs-chicory` test set is green including smoke, resource,
  DV, Host.v1, workflow, and Node parity tests.

## Resource pinning and no-Node smoke

Command:

```bash
./gradlew :quickjs-chicory:clean :quickjs-chicory:jar \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Ran `copyBlueQuickJsWasmResources`.
- Built a jar containing generated pinned Chicory resources.

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*LambdaPackagingSmokeTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Evaluated a deterministic fixture using classpath-pinned resources without
  configuring a filesystem blue-quickjs root in the runtime config.

Command:

```bash
PATH=/usr/bin:/bin ./gradlew :quickjs-chicory:test \
  --tests '*ChicoryBlueQuickJsRuntimeSmokeTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- `command -v node` produced no Node binary on that PATH before the test.
- Chicory smoke fixtures evaluated without Node on PATH.

Command:

```bash
./gradlew :quickjs-chicory:test \
  -PblueQuickJsRoot=/tmp/blue-quickjs \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed after adding resource-copy/classpath resource support.
- Full current `quickjs-chicory` test set remains green.

## Final validation sweep

Command:

```bash
./gradlew clean test \
  -Dblue.quickjs.root=/tmp/blue-quickjs \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Latest full clean run after adding forbidden-surface, OOG, counter-stress, and
  Lambda classpath smoke coverage: `BUILD SUCCESSFUL in 1m 40s`
- 13 actionable tasks executed.
- Covered the existing root test suite plus all current `quickjs-chicory` tests.

Command:

```bash
./gradlew :quickjs-chicory:dependencies --configuration runtimeClasspath
```

Outcome:

- Passed.
- Runtime classpath contains Chicory JVM artifacts only:
  `com.dylibso.chicory:runtime:1.7.5` and
  `com.dylibso.chicory:wasm:1.7.5`.
- No Node, V8, Javet, QuickJs4J, Wasmtime, or JNI runtime dependency appears.

Command:

```bash
python3 - <<'PY'
import yaml
for path in ['.github/workflows/build.yml', '.github/workflows/release.yml']:
    with open(path, 'r', encoding='utf-8') as fh:
        yaml.safe_load(fh)
    print(path, 'ok')
PY
./gradlew :clean :test -Dblue.quickjs.root=/tmp/blue-quickjs
```

Outcome:

- Passed.
- Workflow YAML parsed successfully.
- Root/core-only test path passed:
  `BUILD SUCCESSFUL in 14s`, 6 actionable tasks executed.
- This validates the Java 8-compatible core task path used by the split CI job
  without invoking the Java 11+ optional module.

Lambda-like Docker smoke note:

- The Java 17/21 Lambda container smoke could not be executed in the original
  validation environment.
- The no-Node PATH smoke above is the strongest local substitute performed in
  this environment.

## Remaining environment-limited checks

- `./gradlew clean test` without a `blue.quickjs.root` override still depends on
  the repository default sibling checkout. In this container that path resolves
  to `/blue-quickjs`, but `/` is not writable, so the available checkout is
  `/tmp/blue-quickjs`. The equivalent full clean validation with explicit
  `-Dblue.quickjs.root=/tmp/blue-quickjs` passed.
- Lambda-like Java 17 and Java 21 container smoke tests remain unexecuted in
  this environment.

## Hardening validation

Date: 2026-05-13

Environment updates:

- Java repo branch: `cursor/chicory-blue-quickjs-runtime-1606`
- blue-quickjs checkout: `/Users/piotr/data/blue-quickjs`
- selected engineBuildHash:
  `f91091cb7feb788df340305a877a9cadb0c6f4d13aea8a7da4040b6367d178ea`
- gasVersion: `8`
- executionProfile: `baseline-v1` in generated Java-side metadata

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*BlueQuickJsResourceIntegrityTest' \
  --tests '*ChicoryDocumentHostTest' \
  --tests '*ChicoryHostCallAbiTest' \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs
```

Outcome:

- Passed.
- Covered fail-closed checks for missing filesystem engine hash, wrong engine
  hash, wrong Host.v1 hash, wrong gas version, and wrong execution profile.
- Covered Host.v1 document get/canonical, JSON Pointer escaping, missing/invalid
  pointer behavior, request/response limits, reentrant rejection, and internal
  host failure containment.

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryVsNodeParityTest' \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs
```

Outcome:

- Passed.
- Parity report:
  `quickjs-chicory/build/reports/blue-quickjs-chicory-parity.json`
- The report compares ok/error status, value, normalized VM error
  category/message, `wasmGasUsed`, and `hostGasUsed`.
- No gas mismatches remained in the expanded fixture set.

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*BlueQuickJsSourceWrapperTest' \
  --tests '*ChicoryForbiddenSurfaceTest' \
  --tests '*ChicoryOutOfGasTest' \
  --tests '*LambdaPackagingSmokeTest' \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs
```

Outcome:

- Passed.
- Confirmed source wrapping matches the Node bridge wrapper.
- Confirmed forbidden APIs and out-of-gas boundaries match the Node bridge.
- Confirmed classpath-bundled WASM resources work in a child JVM with
  `PATH=/bin`, where `node` is not available.

Command:

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryBenchmarkReportTest' \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs
```

Outcome:

- Passed.
- Benchmark report:
  `quickjs-chicory/build/reports/blue-quickjs-chicory-benchmarks.json`
- Timing is reported only. Gas equality is asserted.
- Current local timing confirms the expected spike tradeoff: Chicory is much
  slower with fresh WASM instances per evaluation, but gas matched exactly.

Command:

```bash
docker version
```

Outcome:

- Docker client is installed, but the daemon is not reachable:
  `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`.
- Lambda-like Java 17/21 container smoke remains pending.

Command:

```bash
./gradlew :quickjs-chicory:test \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs
```

Outcome:

- Passed.
- Full optional module test suite: `BUILD SUCCESSFUL in 4m 56s`.

## Final stabilization validation

Date: 2026-05-14

Dependency update:

- Root now resolves `blue.language:blue-language-java:2.0.0` from Maven.
- The resolved artifact exposes `ProcessorFatalException.partialResult()` and
  `ProcessorFatalException.totalGas()`.
- Processor-level fatal parity tests use those public accessors directly; no
  reflection-based fatal gas probe remains.

Commands:

```bash
./gradlew test \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs

./gradlew clean build \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs

./gradlew publishToMavenLocal \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs

./gradlew publish \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs

./gradlew :quickjs-chicory:dependencies --configuration runtimeClasspath

./gradlew :quickjs-chicory:test \
  --tests '*LambdaPackagingSmokeTest' \
  -PblueQuickJsRoot=/Users/piotr/data/blue-quickjs \
  -Dblue.quickjs.root=/Users/piotr/data/blue-quickjs

docker version

docker run --rm --platform linux/amd64 \
  -v /Users/piotr/data/blue-contract-java:/workspace \
  -v /Users/piotr/data/blue-quickjs:/blue-quickjs:ro \
  -v /Users/piotr/.gradle:/root/.gradle \
  -w /workspace \
  amazon/aws-sam-cli-build-image-java11:latest \
  /bin/bash -lc './gradlew :quickjs-chicory:test --tests "*LambdaPackagingSmokeTest" -PblueQuickJsRoot=/blue-quickjs -Dblue.quickjs.root=/blue-quickjs'
```

Outcomes:

- All Gradle verification commands above passed.
- `quickjs-chicory/build/reports/blue-quickjs-chicory-parity.json` reported
  `status: passed`, `caseCount: 33`, and no mismatches.
- `quickjs-chicory/build/reports/blue-quickjs-chicory-benchmarks.json` was
  generated as a timing report only; timing is not a pass/fail signal.
- `publishToMavenLocal` produced root and optional Chicory main, sources, and
  javadoc jars.
- `publish` produced staged root and optional Chicory main, sources, javadoc,
  and POM artifacts under `build/staging-deploy`.
- The root POM depends on `blue.language:blue-language-java:2.0.0`,
  `blue.repo:blue-repo-java:1.2.0`, and Jackson; it does not depend on Chicory.
- The optional POM depends on `blue.contract:blue-contract-java`,
  `com.dylibso.chicory:runtime`, and Jackson.
- Docker was initially blocked in the sandbox, but was reachable outside it
  through Docker Desktop 4.73.0.
- The Java 11 AWS SAM Lambda build image smoke passed:
  `BUILD SUCCESSFUL in 28s`.
- The container smoke ran `LambdaPackagingSmokeTest`, which evaluates
  classpath-pinned Chicory without Node on `PATH` and checks for native JS
  runtime dependency leakage.
- A full AWS Lambda runtime/RIE handler invocation smoke is still optional
  follow-up work if release policy requires more than a Java process inside an
  AWS Lambda Java image.
