# Blue Coordination Java

Java processors for executable Blue Coordination repository contracts.

This library lets a Java application process Blue documents that declare
Coordination contracts in their `contracts` map: operations, sequential
workflows, update steps, compute steps, triggered events, composite channels,
embedded scopes, and checkpoints.

The processor is deterministic. Given the same initialized document and the
same ordered input events, it produces the same canonical output document,
triggered events, gas accounting, and output BlueId.

## Install

Gradle:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "blue.coordination:blue-coordination-java:1.0.0"
}
```

The project targets Java 8 bytecode and depends on:

```groovy
api "blue.language:blue-language-java:3.0.0"
api "blue.repo:blue-repo-java:2.0.1"
api "blue.bex:blue-bex-java:1.0.0"
```

## Register Processors

Most applications should register the Coordination processor set on a
repository-configured `Blue` instance:

```java
import blue.coordination.processor.CoordinationProcessors;
import blue.language.Blue;
import blue.repo.BlueRepository;

BlueRepository repository = BlueRepository.v1_3_0();
Blue blue = repository.configure(new Blue());
blue.nodeProvider(repository.nodeProvider());

CoordinationProcessors.registerWith(blue);
```

For direct `DocumentProcessor` construction:

```java
import blue.coordination.processor.CoordinationProcessors;
import blue.language.processor.DocumentProcessor;

DocumentProcessor processor =
        CoordinationProcessors.configure(DocumentProcessor.builder())
                .build();
```

`CoordinationProcessors` intentionally does not register a concrete processor
for `Coordination/Timeline Channel`. Applications should provide their own
timeline provider channel processor or register a small local test processor
for fixtures that use `Coordination/Timeline Entry`.

## Counter Document

This is a complete executable Blue document. The contracts are the program:

```yaml
name: Counter
counter: 0
contracts:
  ownerChannel:
    type: Coordination/Timeline Channel
    timelineId: counter-demo

  increment:
    type: Coordination/Operation
    channel: ownerChannel
    request:
      type: Integer

  incrementImpl:
    type: Coordination/Sequential Workflow Operation
    operation: increment
    steps:
      - name: IncrementAndEmit
        type: Coordination/Compute
        do:
          - $let:
              name: nextCounter
              expr:
                $add:
                  - $document: /counter
                  - $binding:
                      name: event
                      path: /message/request
          - $appendChange:
              op: replace
              path: /counter
              val:
                $var: nextCounter
          - $appendEvent:
              type: Coordination/Chat Message
              message:
                $concat:
                  - Counter is now
                  - " "
                  - $text:
                      $var: nextCounter
          - $return:
              changeset:
                $changeset: true
              events:
                $events: true
```

An input event for that channel looks like this:

```yaml
type: Coordination/Timeline Entry
timeline:
  timelineId: counter-demo
timestamp: 1
message:
  type: Coordination/Operation Request
  operation: increment
  request: 5
```

After processing, `/counter` is `5`, the workflow emits a chat message, and the
channel checkpoint records the delivered timeline entry so duplicates do not
run twice.

## Processing Model

Input:

1. one Blue document;
2. a delivered event, usually a timeline entry or a lifecycle/triggered event.

Output:

1. one canonical Blue document;
2. zero or more triggered events;
3. total gas usage;
4. processing metadata, including the output BlueId.

Processors operate on canonical snapshots instead of process-local mutable
state. You can serialize a processed document, load it again, and continue
processing from the same resolved state.

## Supported Contracts

This library provides executable behavior for:

- `Coordination/Composite Timeline Channel`;
- `Coordination/Operation`;
- `Coordination/Sequential Workflow`;
- `Coordination/Sequential Workflow Operation`;
- `Coordination/Compute`;
- `Coordination/Update Document`;
- `Coordination/Trigger Event`.

The underlying `blue-language-java` runtime provides base behavior used by
Coordination documents:

- `Document Update Channel`;
- `Embedded Node Channel`;
- `Process Embedded`;
- `Channel Event Checkpoint`;
- `Lifecycle Event Channel`;
- `Triggered Event Channel`;
- initialized and terminated markers;
- scope boundaries, patch application, snapshots, gas, and checkpointing.

## BEX In Workflows

`Coordination/Compute` is the BEX execution surface. A Compute step applies a
returned `changeset` directly and emits returned `events` directly, so dynamic
patches and events do not need follow-up Update Document or Trigger Event
steps.

Common workflow bindings:

- `$binding` for `event`, the current `document`, and named step results;
- `$document` for the current document view;
- `$currentContract` for the active workflow contract;
- `$appendChange` and `$changeset` for accumulated patch operations;
- `$appendEvent` and `$events` for accumulated emitted events.

`Coordination/Update Document` accepts literal patch lists only.
`Coordination/Trigger Event` accepts literal event payloads only.

## Build And Test

Run tests:

```bash
./gradlew test
```

Build jars:

```bash
./gradlew build
```

Publish locally:

```bash
./gradlew publishToMavenLocal
```

## Test Coverage

Current test areas:

- processor registration;
- must-understand failures;
- test timeline provider behavior;
- composite timeline routing;
- operation request matching;
- sequential workflow execution;
- compute and BEX execution;
- update document batch application;
- trigger-event execution;
- runtime channels;
- repository-style Counter documents;
- snapshot round-trip stress processing.

## Project Layout

```text
src/main/java/blue/coordination/processor
  CoordinationProcessors.java
  CoordinationProcessorOptions.java
  CoordinationBexIntrinsics.java
  CompositeTimelineChannelProcessor.java
  OperationProcessor.java
  SequentialWorkflowProcessor.java
  SequentialWorkflowOperationProcessor.java
  TimelineProviderSupport.java
  bex/
  merge/
  workflow/
```

## References

- [Blue Language Specification](https://github.com/bluecontract/blue-spec)
- [blue-js open-source processor](https://github.com/bluecontract/blue-js)
