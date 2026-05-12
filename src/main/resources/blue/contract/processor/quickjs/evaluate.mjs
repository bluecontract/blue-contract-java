import { pathToFileURL } from 'node:url';
import path from 'node:path';
import readline from 'node:readline';

const blueQuickJsRoot = process.argv[2];
const BLUE_METADATA_KEYS = new Set([
  'name',
  'description',
  'type',
  'itemType',
  'keyType',
  'valueType',
  'value',
  'items',
  'blue',
  'blueId',
  'schema',
  'mergePolicy',
  '$previous',
  '$pos',
]);

function writeResponse(response) {
  process.stdout.write(`${JSON.stringify(response)}\n`);
}

function fail(message) {
  writeResponse({ ok: false, message });
}

try {
  if (!blueQuickJsRoot) {
    throw new Error('blue-quickjs root argument is required');
  }

  const runtimePath = path.join(
    blueQuickJsRoot,
    'libs/quickjs-runtime/dist/index.js',
  );
  const manifestPath = path.join(
    blueQuickJsRoot,
    'libs/abi-manifest/dist/index.js',
  );
  const runtime = await import(pathToFileURL(runtimePath).href);
  const manifest = await import(pathToFileURL(manifestPath).href);

  const input = readline.createInterface({
    input: process.stdin,
    crlfDelay: Infinity,
  });

  for await (const line of input) {
    if (!line.trim()) {
      continue;
    }
    try {
      const request = JSON.parse(line);
      writeResponse(await evaluateRequest(runtime, manifest, request));
    } catch (error) {
      fail(error instanceof Error ? error.message : String(error));
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}

async function evaluateRequest(runtime, manifest, request) {
  const bindings = request.bindings ?? {};
  const code = sourceFor(request);
  const result = await runtime.evaluate({
    program: {
      code,
      abiId: 'Host.v1',
      abiVersion: 1,
      abiManifestHash: manifest.HOST_V1_HASH,
    },
    input: {
      event: bindings.event ?? null,
      eventCanonical: bindings.eventCanonical ?? null,
      steps: bindings.steps ?? [],
      currentContract: bindings.currentContract ?? null,
      currentContractCanonical: bindings.currentContractCanonical ?? null,
    },
    gasLimit: BigInt(String(request.wasmGasLimit)),
    manifest: manifest.HOST_V1_MANIFEST,
    handlers: {
      document: {
        get: (pointer) =>
          documentResult(bindings.document, pointer, false, bindings.documentMetadata),
        getCanonical: (pointer) =>
          documentResult(bindings.documentCanonical, pointer, true),
      },
      emit: () => ({
        err: {
          code: 'LIMIT_EXCEEDED',
          details: 'emit is not available during expression/code evaluation',
        },
        units: 1,
      }),
    },
  });

  if (!result.ok) {
    return {
      ok: false,
      type: result.type,
      message: result.message,
      wasmGasUsed: result.gasUsed?.toString?.() ?? '0',
    };
  }
  return {
    ok: true,
    value: result.value,
    wasmGasUsed: result.gasUsed.toString(),
    wasmGasRemaining: result.gasRemaining.toString(),
  };
}

function sourceFor(request) {
const prelude = `
const __blueDocument = globalThis.document;
const document = Object.assign(
  (pointer = '/') => __blueDocument(pointer),
  { canonical: (pointer = '/') => __blueDocument.canonical(pointer) },
);
`;
  if (request.mode === 'expression') {
    return `(() => {\n${prelude}\nreturn (${request.code});\n})()`;
  }
  if (request.mode === 'block') {
    return `(() => {\n${prelude}\n${request.code}\n})()`;
  }
  return request.code;
}

function documentResult(root, pointer, canonical = false, metadata = null) {
  const normalized = normalizePointer(pointer);
  if (!canonical && metadata && Object.prototype.hasOwnProperty.call(metadata, normalized)) {
    return { ok: metadata[normalized] ?? null, units: 1 };
  }
  const resolved = getPointer(root, normalized);
  if (!resolved.found) {
    return { ok: null, units: 1 };
  }
  return { ok: canonical ? resolved.value : simpleValue(resolved.value), units: 1 };
}

function getPointer(root, pointer) {
  if (typeof pointer !== 'string' || !pointer.startsWith('/')) {
    return { found: false };
  }
  if (pointer === '/') {
    return { found: true, value: root ?? null };
  }
  let current = root;
  for (const segment of pointer
    .slice(1)
    .split('/')
    .map((part) => part.replace(/~1/g, '/').replace(/~0/g, '~'))) {
    if (Array.isArray(current)) {
      if (!/^(0|[1-9]\d*)$/.test(segment)) {
        return { found: false };
      }
      const index = Number(segment);
      if (index >= current.length) {
        return { found: false };
      }
      current = current[index];
    } else if (
      current !== null &&
      typeof current === 'object' &&
      Object.prototype.hasOwnProperty.call(current, segment)
    ) {
      current = current[segment];
    } else {
      return { found: false };
    }
  }
  return { found: true, value: current ?? null };
}

function normalizePointer(pointer) {
  if (pointer === undefined || pointer === null || pointer === '') {
    return '/';
  }
  if (typeof pointer !== 'string') {
    return null;
  }
  return pointer.startsWith('/') ? pointer : `/${pointer}`;
}

function simpleValue(node) {
  if (node === null || node === undefined) {
    return null;
  }
  if (Array.isArray(node)) {
    return node.map(simpleValue);
  }
  if (typeof node !== 'object') {
    return node;
  }
  if (Object.prototype.hasOwnProperty.call(node, 'value')) {
    return node.value ?? null;
  }
  if (Array.isArray(node.items)) {
    return node.items.map(simpleValue);
  }
  const result = {};
  for (const [key, value] of Object.entries(node)) {
    if (BLUE_METADATA_KEYS.has(key)) {
      continue;
    }
    result[key] = simpleValue(value);
  }
  return result;
}
