# Blue Contract Java

Java processors for executable Blue repository contracts.

This library lets a Java application process Blue documents that contain real
repository contracts such as operations, workflow steps, timeline channels,
triggered events, document updates, embedded scopes, and checkpoints.

Blue processing is deterministic: given the same input document and the same
ordered timeline entries, the processor must produce the same canonical output
document and emit the same events.
This is the compatibility contract across all Blue-compliant document processors —
including this Java processor, the open-source
JavaScript processor in [blue-js](https://github.com/bluecontract/blue-js),
the hosted processor in [MyOS](https://myos.blue), and any other processor.

Read the language and runtime rules in the
[Blue Language Specification](https://language.blue/docs/reference/specification).
Read the timeline/provider model in the
[Timelines white paper](https://language.blue/docs/reference/timelines-white-paper).

## Install

Gradle:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "blue.contract:blue-contract-java:0.1.0-SNAPSHOT"
}
```

This project targets Java 8 bytecode and depends on published artifacts:

```groovy
api "blue.language:blue-language-java:1.0.0"
api "blue.repo:blue-repo-java:1.2.0"
```

## Counter In One Document

The example below is a complete executable Blue document. It declares:

- a concrete timeline provider channel;
- two operations, `increment` and `decrement`;
- two operation-backed sequential workflows;
- one update step that mutates `/counter`;
- one trigger-event step that emits a chat message after each operation.

The channel is a concrete MyOS timeline channel. Do not use
`Conversation/Timeline Channel` directly for executable production processing;
that type is generic timeline vocabulary. Concrete providers such as
`MyOS/MyOS Timeline Channel` own executable timeline routing.

```yaml
name: Counter
counter: 0
contracts:
  ownerChannel:
    type: MyOS/MyOS Timeline Channel
    timelineId: counter-demo

  increment:
    description: Increment the counter by the given number
    type: Conversation/Operation
    channel: ownerChannel
    request:
      type: Integer

  incrementImpl:
    type: Conversation/Sequential Workflow Operation
    operation: increment
    steps:
      - type: Conversation/Update Document
        changeset:
          - op: replace
            path: /counter
            val: ${event.message.request + document('/counter')}

      - type: Conversation/Trigger Event
        event:
          type: Conversation/Chat Message
          message: Counter is now ${document('/counter')}

  decrement:
    description: Decrement the counter by the given number
    type: Conversation/Operation
    channel: ownerChannel
    request:
      type: Integer

  decrementImpl:
    type: Conversation/Sequential Workflow Operation
    operation: decrement
    steps:
      - type: Conversation/Update Document
        changeset:
          - op: replace
            path: /counter
            val: ${document('/counter') - event.message.request}

      - type: Conversation/Trigger Event
        event:
          type: Conversation/Chat Message
          message: Counter is now ${document('/counter')}
```

The document has no code outside the document. The contracts are the program.

## Run It From Java

```java
import blue.contract.processor.BlueDocumentProcessors;
import blue.language.Blue;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.repo.BlueRepository;

public final class CounterExample {
    private static final String COUNTER_DOCUMENT =
            "name: Counter\n" +
            "counter: 0\n" +
            "contracts:\n" +
            "  ownerChannel:\n" +
            "    type: MyOS/MyOS Timeline Channel\n" +
            "    timelineId: counter-demo\n" +
            "  increment:\n" +
            "    description: Increment the counter by the given number\n" +
            "    type: Conversation/Operation\n" +
            "    channel: ownerChannel\n" +
            "    request:\n" +
            "      type: Integer\n" +
            "  incrementImpl:\n" +
            "    type: Conversation/Sequential Workflow Operation\n" +
            "    operation: increment\n" +
            "    steps:\n" +
            "      - type: Conversation/Update Document\n" +
            "        changeset:\n" +
            "          - op: replace\n" +
            "            path: /counter\n" +
            "            val: ${event.message.request + document('/counter')}\n" +
            "      - type: Conversation/Trigger Event\n" +
            "        event:\n" +
            "          type: Conversation/Chat Message\n" +
            "          message: Counter is now ${document('/counter')}\n" +
            "  decrement:\n" +
            "    description: Decrement the counter by the given number\n" +
            "    type: Conversation/Operation\n" +
            "    channel: ownerChannel\n" +
            "    request:\n" +
            "      type: Integer\n" +
            "  decrementImpl:\n" +
            "    type: Conversation/Sequential Workflow Operation\n" +
            "    operation: decrement\n" +
            "    steps:\n" +
            "      - type: Conversation/Update Document\n" +
            "        changeset:\n" +
            "          - op: replace\n" +
            "            path: /counter\n" +
            "            val: ${document('/counter') - event.message.request}\n" +
            "      - type: Conversation/Trigger Event\n" +
            "        event:\n" +
            "          type: Conversation/Chat Message\n" +
            "          message: Counter is now ${document('/counter')}\n";

    public static void main(String[] args) {
        BlueRepository repository = BlueRepository.v1_2_0();

        Blue blue = repository.configure(new Blue());
        blue.nodeProvider(repository.nodeProvider());
        BlueDocumentProcessors.registerWith(blue);

        Node document = blue.yamlToNode(COUNTER_DOCUMENT)
                .blue(repository.typeAliasBlue());
        DocumentProcessingResult initialized =
                blue.initializeDocument(blue.preprocess(document));

        DocumentProcessingResult afterAlice = blue.processDocument(
                initialized.snapshot(),
                operationEntry(blue, repository,
                        "alice-account",
                        "alice@example.com",
                        "increment",
                        5,
                        1L));

        DocumentProcessingResult afterBob = blue.processDocument(
                afterAlice.snapshot(),
                operationEntry(blue, repository,
                        "bob-account",
                        "bob@example.com",
                        "decrement",
                        2,
                        2L));

        System.out.println(afterBob.resolvedDocument().get("/counter")); // 3
        System.out.println(afterAlice.triggeredEvents().get(0).getAsText("/message"));
        System.out.println(afterBob.triggeredEvents().get(0).getAsText("/message"));
        System.out.println(afterBob.blueId());
    }

    private static Node operationEntry(Blue blue,
                                       BlueRepository repository,
                                       String accountId,
                                       String email,
                                       String operation,
                                       int request,
                                       long timestamp) {
        String yaml =
                "type: MyOS/MyOS Timeline Entry\n" +
                "timeline:\n" +
                "  timelineId: counter-demo\n" +
                "timestamp: " + timestamp + "\n" +
                "actor:\n" +
                "  type: MyOS/Principal Actor\n" +
                "  accountId: " + accountId + "\n" +
                "  email: " + email + "\n" +
                "message:\n" +
                "  type: Conversation/Operation Request\n" +
                "  operation: " + operation + "\n" +
                "  request: " + request + "\n";

        return blue.preprocess(blue.yamlToNode(yaml)
                .blue(repository.typeAliasBlue()));
    }
}
```

Alice sends this timeline entry:

```yaml
type: MyOS/MyOS Timeline Entry
timeline:
  timelineId: counter-demo
timestamp: 1
actor:
  type: MyOS/Principal Actor
  accountId: alice-account
  email: alice@example.com
message:
  type: Conversation/Operation Request
  operation: increment
  request: 5
```

Bob sends this timeline entry:

```yaml
type: MyOS/MyOS Timeline Entry
timeline:
  timelineId: counter-demo
timestamp: 2
actor:
  type: MyOS/Principal Actor
  accountId: bob-account
  email: bob@example.com
message:
  type: Conversation/Operation Request
  operation: decrement
  request: 2
```

The result is deterministic:

- after Alice, `/counter == 5`;
- after Bob, `/counter == 3`;
- Alice's operation emits `Counter is now 5`;
- Bob's operation emits `Counter is now 3`;
- checkpoints are written so duplicate timeline entries do not run twice;
- stale timeline entries are rejected by provider recency rules;
- `result.blueId()` is the content address of the canonical output document.

## Deterministic Processing Model

The processor is a deterministic state machine.

Input:

1. one Blue document;
2. a sequence of timeline entries or triggered/lifecycle events.

Output:

1. one canonical Blue document;
2. zero or more triggered events;
3. a BlueId for the output document;
4. gas accounting and checkpoints.

For any document that is understood by all runtimes, this Java processor, the
open-source [blue-js](https://github.com/bluecontract/blue-js) processor, and
MyOS hosted processing are expected to produce the same output document and the
same triggered events for the same ordered inputs. A mismatch is a processor
bug or a version mismatch.

This is why document processors operate on canonical snapshots instead of
process-local mutable state. You can serialize `result.canonicalDocument()`,
load it again, and continue processing from the same resolved snapshot.

## Timelines And Fetching

The document processor does not guess which external events exist. It processes
entries that are delivered to it.

Timeline providers are responsible for fetching and ordering timeline entries.
A concrete provider channel, such as `MyOS/MyOS Timeline Channel`, tells the
processor how to recognize entries from that provider and when an entry is new
relative to the channel checkpoint.

Read the
[Timelines white paper](https://language.blue/docs/reference/timelines-white-paper)
for the full model: timeline entries, provider completeness, fetching,
ordering, and how concrete timeline providers hand deterministic event streams
to processors.

## MyOS SaaS

[MyOS](https://myos.blue) is the hosted SaaS environment for processing Blue
documents, timelines, and workflows. After signing up, switch to
`Developer Mode` from the top-right corner to access developer-oriented tools.

Developer documentation is available at
[developers.myos.blue](https://developers.myos.blue/).

The goal is portability:

- run locally in Java with this package;
- run locally or in services with [blue-js](https://github.com/bluecontract/blue-js);
- run hosted in MyOS.

For the same supported document, repository version, and timeline entries, the
canonical output and triggered events should be the same.

## What Is Supported

This repository currently provides executable behavior for:

- `Conversation/Composite Timeline Channel`;
- `Conversation/Operation`;
- `Conversation/Sequential Workflow`;
- `Conversation/Sequential Workflow Operation`;
- `Conversation/Update Document`;
- `Conversation/Trigger Event`;
- `Conversation/JavaScript Code`;
- `MyOS/MyOS Timeline Channel`.

The underlying `blue-language-java` runtime provides Core processing behavior
used by real repository contracts:

- `Core/Document Update Channel`;
- `Core/Embedded Node Channel`;
- `Core/Process Embedded`;
- `Core/Channel Event Checkpoint`;
- `Core/Lifecycle Event Channel`;
- `Core/Triggered Event Channel`;
- processing initialized and terminated markers;
- scope boundaries, patch application, snapshots, gas, and checkpointing.

## JavaScript In Workflows

`Conversation/Update Document` expressions and `Conversation/JavaScript Code`
steps run through QuickJS.

Available bindings:

- `event`;
- `eventCanonical`;
- `document(pointer)`;
- `document.canonical(pointer)`;
- `steps`;
- `currentContract`;
- `currentContractCanonical`.

Example JavaScript code step:

```yaml
- name: CreateMessage
  type: Conversation/JavaScript Code
  code: |-
    const message =
      `Counter is now ${document('/counter')}`;

    return {
      events: [
        {
          type: "Conversation/Chat Message",
          message,
        },
      ],
    };
```

The Java implementation currently uses a persistent Node bridge to a local
`blue-quickjs` checkout. By default it expects:

```text
../blue-quickjs
```

Override the location if needed:

```bash
./gradlew test -Dblue.quickjs.root=/path/to/blue-quickjs
```

Build the QuickJS runtime first:

```bash
cd ../blue-quickjs
pnpm nx build quickjs-runtime
```

## Registration

Most applications should use the one-call facade:

```java
BlueRepository repository = BlueRepository.v1_2_0();

Blue blue = repository.configure(new Blue());
blue.nodeProvider(repository.nodeProvider());
BlueDocumentProcessors.registerWith(blue);
```

For custom processor construction:

```java
import blue.contract.processor.BlueDocumentProcessors;
import blue.language.processor.DocumentProcessor;

DocumentProcessor processor =
        BlueDocumentProcessors.configure(DocumentProcessor.builder())
                .build();
```

You can register processor groups directly when needed:

```java
ConversationProcessors.registerWith(blue);
MyOSProcessors.registerWith(blue);
```

## Core Concepts For Developers

### Documents are data and programs

A Blue document is regular data. Its `contracts` map declares executable
behavior. A processor does not need app-specific Java code for every workflow;
it reads the contract graph and executes supported contracts.

### Operations are messages

An operation is invoked by a `Conversation/Operation Request`, usually carried
as the `message` of a timeline entry:

```yaml
message:
  type: Conversation/Operation Request
  operation: increment
  request: 5
```

The handler contract references the operation:

```yaml
incrementImpl:
  type: Conversation/Sequential Workflow Operation
  operation: increment
```

### Channels route events

Handlers bind to channels. A channel decides whether a delivered event belongs
to that channel. Provider channels also define recency rules so stale entries do
not re-run handlers.

### Workflows are ordered steps

Sequential workflows execute step by step. Later steps can read previous named
step results through `steps`.

### Triggered events continue processing

`Conversation/Trigger Event` emits a Blue event. Consumers bound to
`Core/Triggered Event Channel` can react to it in the same processing run.

## Build And Test

```bash
./gradlew test
```

Build jars:

```bash
./gradlew build
```

Current test areas:

- processor registration;
- must-understand failures;
- MyOS timeline matching;
- concrete test timeline provider behavior;
- composite timeline channel routing;
- operation request matching;
- sequential workflow execution;
- trigger-event execution;
- JavaScript code execution;
- Core runtime channels;
- repository-style Counter documents;
- snapshot round-trip stress processing.

## Project Layout

```text
src/main/java/blue/contract/processor
  BlueDocumentProcessors.java
  ConversationProcessors.java
  MyOSProcessors.java

src/main/java/blue/contract/processor/conversation
  CompositeTimelineChannelProcessor.java
  OperationProcessor.java
  OperationRequestMatcher.java
  SequentialWorkflowProcessor.java
  SequentialWorkflowOperationProcessor.java
  TimelineProviderSupport.java
  workflow/
  expression/
  javascript/

src/main/java/blue/contract/processor/myos
  MyOSTimelineChannelProcessor.java
```

## References

- [Blue Language Specification](https://language.blue/docs/reference/specification)
- [Timelines white paper](https://language.blue/docs/reference/timelines-white-paper)
- [blue-js open-source processor](https://github.com/bluecontract/blue-js)
- [MyOS SaaS](https://myos.blue)
- [MyOS developer documentation](https://developers.myos.blue/)
