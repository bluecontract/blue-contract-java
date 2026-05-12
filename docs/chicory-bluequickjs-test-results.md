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

These are intentionally left pending until the corresponding implementation
phases exist:

```bash
./gradlew :quickjs-chicory:clean :quickjs-chicory:test \
  -PblueQuickJsRoot=/tmp/blue-quickjs \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

```bash
./gradlew :quickjs-chicory:test \
  --tests '*ChicoryVsNodeParityTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs \
  -Dblue.quickjs.root=/tmp/blue-quickjs
```

```bash
./gradlew :quickjs-chicory:test \
  --tests '*BlueQuickJsResourceIntegrityTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

```bash
./gradlew clean test
```

```bash
PATH=/usr/bin:/bin ./gradlew :quickjs-chicory:test \
  --tests '*ChicoryBlueQuickJsRuntimeSmokeTest' \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

```bash
./gradlew :quickjs-chicory:clean :quickjs-chicory:jar \
  -PblueQuickJsRoot=/tmp/blue-quickjs
```

## Pending artifacts

- `quickjs-chicory/build/reports/blue-quickjs-chicory-parity.json`
- Lambda-like Java 17 smoke output
- Lambda-like Java 21 smoke output
