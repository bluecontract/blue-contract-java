# Chicory blue-quickjs spike

This document records the implementation facts, assumptions, blockers, and parity
results for the experimental Java/Chicory blue-quickjs runtime.

## Preflight

- Java repo branch: `feature/chicory-bluequickjs-wasm-runtime`
- Java runtime used locally: OpenJDK 21.0.10
- Gradle wrapper: 8.4
- Requested default sibling checkout `../blue-quickjs` resolves to `/blue-quickjs`
  in this container, but `/` is not writable. The local preflight checkout is:
  `/tmp/blue-quickjs`
- blue-quickjs repository: `https://github.com/bluecontract/blue-quickjs`
- blue-quickjs commit: `d462a11818049d7909bbe3ceb36bddd2b532e9cd`
- blue-quickjs QuickJS submodule commit:
  `9d1eda6e0d1ec36c279d87380db77fbcc3acbae8`

## blue-quickjs build commands

The checked-out `package.json`, `nx.json`, and `libs/quickjs-wasm-build/project.json`
were inspected before running build commands.

Commands run:

```bash
pnpm install --frozen-lockfile
bash tools/scripts/setup-emsdk.sh
WASM_VARIANTS=wasm32 WASM_BUILD_TYPES=release pnpm exec nx build quickjs-wasm-build
pnpm exec nx build quickjs-wasm
pnpm exec nx build abi-manifest
pnpm exec nx build quickjs-runtime
```

## Canonical artifact

- Selected wasm artifact:
  `/tmp/blue-quickjs/libs/quickjs-wasm-build/dist/quickjs-eval.wasm`
- Packaged copy:
  `/tmp/blue-quickjs/libs/quickjs-wasm/dist/wasm/quickjs-eval.wasm`
- Metadata:
  `/tmp/blue-quickjs/libs/quickjs-wasm-build/dist/quickjs-wasm-build.metadata.json`
- Wasm magic bytes: `00 61 73 6d`
- Wasm version bytes: `01 00 00 00`
- Wasm size: `659086`
- Variant: `wasm32`
- Build type: `release`
- engineBuildHash / SHA-256:
  `1d4584fc0552a24ee840afa2cca9f1536d47429f467585d4d5c1a5236ba96dc9`
- Loader SHA-256:
  `11a13f0414e7387f0c9502c8c0ca9479473505d94b824356d015a8d8007637fb`
- Emscripten version: `3.1.56`
- QuickJS version: `2025-09-13`
- Fixed memory:
  - initial: `33554432`
  - maximum: `33554432`
  - stack: `1048576`
  - allowGrowth: `false`
- Determinism flags:
  - `-sFILESYSTEM=0`
  - `-sALLOW_MEMORY_GROWTH=0`
  - `-sINITIAL_MEMORY=33554432`
  - `-sMAXIMUM_MEMORY=33554432`
  - `-sSTACK_SIZE=1048576`
  - `-sALLOW_TABLE_GROWTH=0`
  - `-sENVIRONMENT=node,web`
  - `-sNO_EXIT_RUNTIME=1`

## Host.v1 ABI

- ABI id: `Host.v1`
- ABI version: `1`
- abiManifestHash / `HOST_V1_HASH`:
  `e23b0b2ee169900bbde7aff78e6ce20fead1715c60f8a8e3106d9959450a3d34`
- Function IDs:
  - `1`: `document.get`
  - `2`: `document.getCanonical`
  - `3`: `emit`

## Gas/profile metadata

- Gas version from blue-quickjs documentation: `JS_GAS_VERSION_LATEST = 2`
- Execution profile from blue-quickjs documentation: deterministic Baseline #1
- Current build metadata does not yet include explicit `gasVersion` or
  `executionProfile` fields. The Java runtime must not silently infer them from
  docs during normal evaluation; it must fail closed unless the generated/pinned
  Java-side metadata includes these values or upstream metadata grows these
  fields.

## Wasm imports

Parsed directly from `quickjs-eval.wasm`:

| Module | Name | Signature | Notes |
| --- | --- | --- | --- |
| `env` | `abort` | `() -> ()` | Emscripten support import; should be deterministic fatal if invoked. |
| `env` | `__assert_fail` | `(i32, i32, i32, i32) -> ()` | Emscripten support import; should be deterministic fatal if invoked. |
| `host` | `host_call` | `(i32, i32, i32, i32, i32) -> i32` | Required Host.v1 dispatcher import. |
| `env` | `emscripten_date_now` | `() -> f64` | Emscripten support import; should be a deterministic stub and must not expose wall-clock time. |
| `env` | `emscripten_resize_heap` | `(i32) -> i32` | Memory growth is disabled; should return failure deterministically. |

## Wasm exports

Parsed directly from `quickjs-eval.wasm`:

- `memory`
- `__wasm_call_ctors`
- `malloc`
- `free`
- `__indirect_function_table`
- `qjs_det_init`
- `qjs_det_eval`
- `qjs_det_set_gas_limit`
- `qjs_det_free`
- `qjs_det_enable_tape`
- `qjs_det_read_tape`
- `qjs_det_enable_trace`
- `qjs_det_read_trace`
- `stackSave`
- `stackRestore`
- `stackAlloc`

## Baseline test result

Command:

```bash
./gradlew clean test -Dblue.quickjs.root=/tmp/blue-quickjs
```

Result:

- `BUILD SUCCESSFUL in 33s`
- 6 actionable tasks executed
- Existing Node bridge tests passed.

Representative deterministic stress output:

- QuickJS counter snapshot round-trip stress:
  - iterations: `100`
  - totalGas: `18700`
  - minGas: `187`
  - maxGas: `187`
  - finalBlueId: `qQgDoUkPVc2QPWEJar82QSy2kUahX8HHZJHDWivHmEM`
- No-JS counter snapshot round-trip stress:
  - iterations: `100`
  - totalGas: `18100`
  - minGas: `181`
  - maxGas: `181`
  - finalBlueId: `9kr8UvMAUAZ2wdk3EreY2ShtC5Q8927uaBdn9QTxqxyj`

## Current deviations and risks

1. The local checkout is in `/tmp/blue-quickjs` because `/blue-quickjs` cannot be
   created in this container. All local validation commands should pass
   `-Dblue.quickjs.root=/tmp/blue-quickjs` or `-PblueQuickJsRoot=/tmp/blue-quickjs`.
2. The raw wasm has deterministic Emscripten support imports in `env` in addition
   to `host.host_call`. The Chicory adapter must explicitly implement or reject
   these imports; it must not run the Emscripten JS loader under Node.
3. Explicit `gasVersion` and `executionProfile` metadata fields are absent from
   the generated blue-quickjs metadata observed during preflight. Runtime pinning
   must address this fail-closed requirement before evaluation.
