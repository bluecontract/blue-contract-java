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
- `BUILD SUCCESSFUL in 1m 16s`
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

- `docker` is not installed in this VM, so the Java 17/21 Lambda container smoke
  could not be executed here.
- The no-Node PATH smoke above is the strongest local substitute performed in
  this environment.

## Remaining environment-limited checks

- `./gradlew clean test` without a `blue.quickjs.root` override still depends on
  the repository default sibling checkout. In this container that path resolves
  to `/blue-quickjs`, but `/` is not writable, so the available checkout is
  `/tmp/blue-quickjs`. The equivalent full clean validation with explicit
  `-Dblue.quickjs.root=/tmp/blue-quickjs` passed.
- Lambda-like Java 17 and Java 21 container smoke tests remain unexecuted because
  Docker is not installed in this VM.
