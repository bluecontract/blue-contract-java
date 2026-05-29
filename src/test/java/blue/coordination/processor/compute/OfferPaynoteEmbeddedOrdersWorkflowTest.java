package blue.coordination.processor.compute;

import blue.coordination.processor.CoordinationProcessorOptions;
import blue.coordination.processor.bex.BexProcessingMetrics;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import blue.language.processor.ProcessorStatus;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario:
 * A package order for Customer and Travel Agency sells a 20-21 June weekend package: Deluxe Room in
 * Hotel Badura plus a 250zl Dinner for Two at Restaurant Cud Malina for 499 PLN.
 *
 * Main flow:
 * 1. Travel Agency delivers the exact Package PayNote as the operation request.
 * 2. Card Processor authorizes the embedded PayNote.
 * 3. Travel Agency provides the Restaurant Order and Hotel Order as separate operation requests inside
 *    the embedded PayNote. The orders are not root templates.
 * 4. Restaurant and Hotel confirm their own embedded orders.
 * 5. PayNote listens to the embedded order channels and requests capture only after both confirmations.
 * 6. Card Processor confirms capture.
 * 7. The package order listens to the embedded PayNote and changes order status to {@code Ready to use}.
 *
 * Actors and operations:
 * - Customer and Travel Agency are package-order participants.
 * - Travel Agency calls {@code deliverPaynote}, {@code provideRestaurantOrder}, and
 *   {@code provideHotelOrder}.
 * - Card Processor calls {@code confirmAuthorization} and {@code confirmCapture}.
 * - Restaurant and Hotel each call {@code confirm} inside their embedded order scopes.
 */
class OfferPaynoteEmbeddedOrdersWorkflowTest {
    private static final String DOCUMENT_RESOURCE =
            "coordination/compute/offer-paynote-embedded-orders-bex.yaml";

    @Test
    void packageOrderBecomesReadyToUseAfterPaynoteCapturesConfirmedRestaurantAndHotelOrders() {
        BexProcessingMetrics metrics = new BexProcessingMetrics();
        ComputeWorkflowTestSupport support = support(metrics);

        Node authored = support.yamlResource(DOCUMENT_RESOURCE);
        assertNoRootTemplates(authored);
        Node current = support.initialize(authored).document();
        assertEquals("Awaiting PayNote", current.get("/order/status"));
        assertEquals("20-21 June weekend", current.get("/package/title"));
        assertEquals("Deluxe Room", current.get("/package/roomType"));
        assertEquals("Restaurant Cud Malina", current.get("/package/restaurantName"));
        assertEquals(BigInteger.valueOf(499), current.get("/package/price/amount"));
        long snapshotBuildsAfterInitialize = metrics.processingSnapshotFromDocumentBuilds();

        // Travel Agency delivers the PayNote directly in the operation request. The root order embeds
        // that request at /paynote and asks Card Processor to authorize 499 PLN.
        DocumentProcessingResult paynoteDelivered = processMeasured(metrics, "deliverPaynote", support, current,
                operationEvent(support, "travel-agency", 1, "deliverPaynote", packagePaynote(support)));
        assertFalse(paynoteDelivered.capabilityFailure(), paynoteDelivered.failureReason());
        current = paynoteDelivered.document();
        assertEquals("Waiting for PayNote capture", current.get("/order/status"));
        assertEquals(Boolean.TRUE, current.get("/order/paynoteDelivered"));
        assertEquals("Package PayNote", current.get("/paynote/name"));
        assertEquals("/paynote", current.get("/contracts/embeddedPaynotes/paths/0"));
        assertContainsEventKind(paynoteDelivered.triggeredEvents(), "PayNote Authorization Requested");

        // Card Processor authorizes the PayNote. Before this point, component orders are illegal.
        DocumentProcessingResult authorized = processMeasured(metrics, "confirmAuthorization", support, current,
                operationEvent(support, "card-processor", 2, "confirmAuthorization", new Node()));
        assertFalse(authorized.capabilityFailure(), authorized.failureReason());
        current = authorized.document();
        assertEquals("Authorized", current.get("/paynote/status"));

        // Travel Agency provides the restaurant document as a request to PayNote.
        DocumentProcessingResult restaurantProvided = processMeasured(metrics, "provideRestaurantOrder", support, current,
                operationEvent(support, "travel-agency", 3, "provideRestaurantOrder", restaurantOrder(support)));
        assertFalse(restaurantProvided.capabilityFailure(), restaurantProvided.failureReason());
        current = restaurantProvided.document();
        assertEquals("Restaurant Order", current.get("/paynote/restaurantOrder/name"));
        assertEquals(Boolean.TRUE, current.get("/paynote/restaurantOrderProvided"));
        assertEquals("/restaurantOrder", current.get("/paynote/contracts/componentOrders/paths/0"));

        // Travel Agency provides the hotel document as a separate request to PayNote.
        DocumentProcessingResult hotelProvided = processMeasured(metrics, "provideHotelOrder", support, current,
                operationEvent(support, "travel-agency", 4, "provideHotelOrder", hotelOrder(support)));
        assertFalse(hotelProvided.capabilityFailure(), hotelProvided.failureReason());
        current = hotelProvided.document();
        assertEquals("Hotel Order", current.get("/paynote/hotelOrder/name"));
        assertEquals(Boolean.TRUE, current.get("/paynote/hotelOrderProvided"));
        assertEquals("/hotelOrder", current.get("/paynote/contracts/componentOrders/paths/1"));

        // Restaurant confirms the restaurant order. PayNote notices the embedded event, but capture
        // is still blocked because the hotel order has not confirmed yet.
        DocumentProcessingResult restaurantConfirmed = processMeasured(metrics, "restaurantConfirm", support, current,
                operationEvent(support, "restaurant", 5, "confirm", new Node()));
        assertFalse(restaurantConfirmed.capabilityFailure(), restaurantConfirmed.failureReason());
        current = restaurantConfirmed.document();
        assertEquals("Confirmed", current.get("/paynote/restaurantOrder/status"));
        assertEquals(Boolean.TRUE, current.get("/paynote/restaurantConfirmed"));
        assertEquals(Boolean.FALSE, current.get("/paynote/captureRequested"));

        // Hotel confirms the hotel order. Now both embedded confirmations exist, so PayNote emits a
        // capture request for Card Processor.
        DocumentProcessingResult hotelConfirmed = processMeasured(metrics, "hotelConfirm", support, current,
                operationEvent(support, "hotel", 6, "confirm", new Node()));
        assertFalse(hotelConfirmed.capabilityFailure(), hotelConfirmed.failureReason());
        current = hotelConfirmed.document();
        assertEquals("Confirmed", current.get("/paynote/hotelOrder/status"));
        assertEquals(Boolean.TRUE, current.get("/paynote/hotelConfirmed"));
        assertEquals(Boolean.TRUE, current.get("/paynote/captureRequested"));

        // Card Processor confirms capture. The root package order observes /paynote/captured through
        // a Document Update Channel and switches to Ready to use.
        DocumentProcessingResult captured = processMeasured(metrics, "confirmCapture", support, current,
                operationEvent(support, "card-processor", 7, "confirmCapture", new Node()));
        assertFalse(captured.capabilityFailure(), captured.failureReason());
        current = captured.document();
        assertEquals("Captured", current.get("/paynote/status"));
        assertEquals(Boolean.TRUE, current.get("/paynote/captured"));
        assertEquals("Ready to use", current.get("/order/status"));
        assertContainsEventKind(captured.triggeredEvents(), "Package Order Ready to Use");

        assertEquals(0L, metrics.updateIndividualPatchApplications());
        assertEquals(metrics.updateBatchPatchApplications(), metrics.directBexChangesetHits());
        assertEquals(0L, metrics.bexDocumentViewMaterializedHits());
        assertEquals(0L, metrics.bexSyntheticProgramMaterializations());
        assertEquals(0L, metrics.workflowDocumentViewsFromDocument());
        assertEquals(0L, metrics.workflowDocumentViewMisses());
        assertTrue(metrics.bexDocumentViewFrozenDirectHits() > 0L);
        assertEquals(snapshotBuildsAfterInitialize, metrics.processingSnapshotFromDocumentBuilds());
    }

    @Test
    void illegalPackagePaynoteAndComponentOrderOperationsFailClosed() {
        ComputeWorkflowTestSupport support = support(null);
        Node current = support.initialize(support.yamlResource(DOCUMENT_RESOURCE)).document();

        // Illegal: wrong PayNote amount. The package order only accepts the exact 499 PLN PayNote for
        // this Hotel Badura + Cud Malina weekend package. This is rejected by deliverPaynote.request
        // matching, so the workflow does not run and the document is unchanged.
        Node wrongPaynote = packagePaynote(support);
        wrongPaynote.getProperties().put("amount", new Node().value(498));
        DocumentProcessingResult wrongPaynoteResult = support.blue.processDocument(current,
                operationEvent(support, "travel-agency", 11, "deliverPaynote", wrongPaynote));
        assertFalse(wrongPaynoteResult.capabilityFailure(), wrongPaynoteResult.failureReason());
        assertFalse(wrongPaynoteResult.document().getProperties().containsKey("paynote"));
        assertEquals("Awaiting PayNote", wrongPaynoteResult.document().get("/order/status"));

        current = support.blue.processDocument(current,
                operationEvent(support, "travel-agency", 12, "deliverPaynote", packagePaynote(support))).document();

        // Illegal: Travel Agency cannot provide component orders until Card Processor authorizes the
        // embedded PayNote.
        Node pendingAuthorization = current;
        DocumentProcessingResult beforeAuthorization = support.blue.processDocument(pendingAuthorization,
                operationEvent(support, "travel-agency", 13, "provideHotelOrder", hotelOrder(support)));
        assertRuntimeFatal(beforeAuthorization, "after PayNote authorization");

        current = support.blue.processDocument(current,
                operationEvent(support, "card-processor", 14, "confirmAuthorization", new Node())).document();

        // Illegal: provideRestaurantOrder rejects a hotel document at operation-request matching time.
        // Restaurant and hotel fulfillment documents are intentionally specific and not interchangeable.
        DocumentProcessingResult wrongRestaurantDocument = support.blue.processDocument(current,
                operationEvent(support, "travel-agency", 15, "provideRestaurantOrder", hotelOrder(support)));
        assertFalse(wrongRestaurantDocument.capabilityFailure(), wrongRestaurantDocument.failureReason());
        assertFalse(wrongRestaurantDocument.document().getAsNode("/paynote").getProperties()
                .containsKey("restaurantOrder"));
        assertEquals(Boolean.FALSE, wrongRestaurantDocument.document().get("/paynote/restaurantOrderProvided"));

        current = support.blue.processDocument(current,
                operationEvent(support, "travel-agency", 16, "provideRestaurantOrder", restaurantOrder(support))).document();
        current = support.blue.processDocument(current,
                operationEvent(support, "travel-agency", 17, "provideHotelOrder", hotelOrder(support))).document();

        // Illegal: Card Processor cannot capture before both Restaurant and Hotel have confirmed.
        Node beforeCaptureRequested = current;
        DocumentProcessingResult earlyCapture = support.blue.processDocument(beforeCaptureRequested,
                operationEvent(support, "card-processor", 18, "confirmCapture", new Node()));
        assertRuntimeFatal(earlyCapture, "before both orders confirm");
    }

    private static ComputeWorkflowTestSupport support(BexProcessingMetrics metrics) {
        CoordinationProcessorOptions.Builder builder = CoordinationProcessorOptions.builder();
        if (metrics != null) {
            builder.processingMetrics(metrics);
        }
        return ComputeWorkflowTestSupport.create(builder.build());
    }

    private static DocumentProcessingResult processMeasured(BexProcessingMetrics metrics,
                                                            String label,
                                                            ComputeWorkflowTestSupport support,
                                                            Node document,
                                                            Node event) {
        BexProcessingMetrics.Snapshot before = metrics.snapshot();
        long start = System.nanoTime();
        DocumentProcessingResult result = support.blue.processDocument(document, event);
        long wallNanos = System.nanoTime() - start;
        BexProcessingMetrics.Snapshot after = metrics.snapshot();
        printStepMetrics(label, wallNanos, result, before, after);
        return result;
    }

    private static void printStepMetrics(String label,
                                         long wallNanos,
                                         DocumentProcessingResult result,
                                         BexProcessingMetrics.Snapshot before,
                                         BexProcessingMetrics.Snapshot after) {
        System.out.printf(Locale.ROOT,
                "[offer-paynote metrics] %s wall=%.3fms status=%s gas=%d events=%d snapshot=%s failure=%s%n",
                label,
                nanosToMs(wallNanos),
                result.status(),
                result.totalGas(),
                result.triggeredEvents().size(),
                result.snapshot() != null,
                result.failureReason());
        System.out.printf(Locale.ROOT,
                "  processor blue=%.3fms process=%.3fms preprocess=%.3fms bundle=%.3fms actualBundle=%.3fms reuse=%.3fms cacheKey=%.3fms bundleHits=%d bundleMisses=%d built=%d reused=%d%n",
                ms(after.blueProcessDocumentNanos, before.blueProcessDocumentNanos),
                ms(after.processDocumentNanos, before.processDocumentNanos),
                ms(after.eventPreprocessNanos, before.eventPreprocessNanos),
                ms(after.bundleLoadNanos, before.bundleLoadNanos),
                ms(after.bundleLoadActualBuildNanos, before.bundleLoadActualBuildNanos),
                ms(after.bundleLoadReuseNanos, before.bundleLoadReuseNanos),
                ms(after.bundleLoadCacheKeyBuildNanos, before.bundleLoadCacheKeyBuildNanos),
                delta(after.bundleLoadCacheHits, before.bundleLoadCacheHits),
                delta(after.bundleLoadCacheMisses, before.bundleLoadCacheMisses),
                delta(after.bundlesBuilt, before.bundlesBuilt),
                delta(after.bundlesReused, before.bundlesReused));
        System.out.printf(Locale.ROOT,
                "  snapshotCache lookup=%.3fms hits=%d misses=%d fromDocument=%.3fms builds=%d bundleScope attempts=%d execHits=%d refreshes=%d termination=%.3fms resolved=%.3fms contractLoad=%.3fms%n",
                ms(after.processingSnapshotCacheLookupNanos, before.processingSnapshotCacheLookupNanos),
                delta(after.processingSnapshotCacheHits, before.processingSnapshotCacheHits),
                delta(after.processingSnapshotCacheMisses, before.processingSnapshotCacheMisses),
                ms(after.processingSnapshotFromDocumentNanos, before.processingSnapshotFromDocumentNanos),
                delta(after.processingSnapshotFromDocumentBuilds, before.processingSnapshotFromDocumentBuilds),
                delta(after.bundleScopeLoadAttempts, before.bundleScopeLoadAttempts),
                delta(after.bundleScopeExecutionCacheHits, before.bundleScopeExecutionCacheHits),
                delta(after.bundleScopeRefreshes, before.bundleScopeRefreshes),
                ms(after.bundleScopeTerminationCheckNanos, before.bundleScopeTerminationCheckNanos),
                ms(after.bundleScopeResolvedLookupNanos, before.bundleScopeResolvedLookupNanos),
                ms(after.bundleScopeContractLoadNanos, before.bundleScopeContractLoadNanos));
        System.out.printf(Locale.ROOT,
                "  routing channelDiscovery=%.3fms channelMatch=%.3fms channelEvals=%d handlerDiscovery=%.3fms handlerMatch=%.3fms handlerAttempts=%d handlerExecution=%.3fms handlers=%d eventRouting=%.3fms routed=%d%n",
                ms(after.channelDiscoveryNanos, before.channelDiscoveryNanos),
                ms(after.channelMatchNanos, before.channelMatchNanos),
                delta(after.channelEvaluations, before.channelEvaluations),
                ms(after.handlerDiscoveryNanos, before.handlerDiscoveryNanos),
                ms(after.handlerMatchNanos, before.handlerMatchNanos),
                delta(after.handlerMatchAttempts, before.handlerMatchAttempts),
                ms(after.handlerExecutionNanos, before.handlerExecutionNanos),
                delta(after.handlersExecuted, before.handlersExecuted),
                ms(after.triggeredEventRoutingNanos, before.triggeredEventRoutingNanos),
                delta(after.triggeredEventsRouted, before.triggeredEventsRouted));
        System.out.printf(Locale.ROOT,
                "  workflow runner=%.3fms steps=%d computeSteps=%d updateSteps=%d triggerSteps=%d compute=%.3fms update=%.3fms trigger=%.3fms checkpoint=%.3fms snapshot=%.3fms post=%.3fms%n",
                ms(after.workflowRunnerNanos, before.workflowRunnerNanos),
                delta(after.workflowStepsExecuted, before.workflowStepsExecuted),
                delta(after.computeStepsExecuted, before.computeStepsExecuted),
                delta(after.updateDocumentStepsExecuted, before.updateDocumentStepsExecuted),
                delta(after.triggerEventStepsExecuted, before.triggerEventStepsExecuted),
                ms(after.computeStepNanos, before.computeStepNanos),
                ms(after.updateStepNanos, before.updateStepNanos),
                ms(after.triggerStepNanos, before.triggerStepNanos),
                ms(after.checkpointUpdateNanos, before.checkpointUpdateNanos),
                ms(after.snapshotCommitNanos, before.snapshotCommitNanos),
                ms(after.postProcessingNanos, before.postProcessingNanos));
        System.out.printf(Locale.ROOT,
                "  checkpoint phases ensure=%.3fms find=%.3fms currentIdentity=%.3fms isNewer=%.3fms duplicate=%.3fms persist=%.3fms identityCache hits=%d misses=%d storedHits=%d storedMisses=%d directBlueId=%.3fms contentBlueId=%.3fms fallback=%.3fms%n",
                ms(after.checkpointEnsureNanos, before.checkpointEnsureNanos),
                ms(after.checkpointFindNanos, before.checkpointFindNanos),
                ms(after.checkpointCurrentIdentityNanos, before.checkpointCurrentIdentityNanos),
                ms(after.checkpointIsNewerNanos, before.checkpointIsNewerNanos),
                ms(after.checkpointDuplicateNanos, before.checkpointDuplicateNanos),
                ms(after.checkpointPersistNanos, before.checkpointPersistNanos),
                delta(after.checkpointIdentityCacheHits, before.checkpointIdentityCacheHits),
                delta(after.checkpointIdentityCacheMisses, before.checkpointIdentityCacheMisses),
                delta(after.checkpointStoredIdentityCacheHits, before.checkpointStoredIdentityCacheHits),
                delta(after.checkpointStoredIdentityCacheMisses, before.checkpointStoredIdentityCacheMisses),
                ms(after.checkpointDirectBlueIdNanos, before.checkpointDirectBlueIdNanos),
                ms(after.checkpointContentBlueIdNanos, before.checkpointContentBlueIdNanos),
                ms(after.checkpointFallbackNanos, before.checkpointFallbackNanos));
        System.out.printf(Locale.ROOT,
                "  bex compileExecute=%.3fms compile=%.3fms execute=%.3fms compiled=%d cacheHits=%d cacheMisses=%d nodeWriter=%.3fms syntheticProgramMaterializations=%d directChangesets=%d%n",
                ms(after.computeCompileExecuteNanos, before.computeCompileExecuteNanos),
                ms(after.bexCompileNanos, before.bexCompileNanos),
                ms(after.bexExecuteNanos, before.bexExecuteNanos),
                delta(after.bexCompiledExecutions, before.bexCompiledExecutions),
                delta(after.bexCompileCacheHits, before.bexCompileCacheHits),
                delta(after.bexCompileCacheMisses, before.bexCompileCacheMisses),
                ms(after.bexNodeWriterNanos, before.bexNodeWriterNanos),
                delta(after.bexSyntheticProgramMaterializations, before.bexSyntheticProgramMaterializations),
                delta(after.directBexChangesetHits, before.directBexChangesetHits));
        System.out.printf(Locale.ROOT,
                "  patches applied=%d batch=%d individual=%d conversion=%.3fms apply=%.3fms batchPlan=%.3fms batchConform=%.3fms batchBuild=%.3fms batchCommit=%.3fms boundary=%.3fms gas=%.3fms updateRouting=%.3fms%n",
                delta(after.patchesApplied, before.patchesApplied),
                delta(after.updateBatchPatchApplications, before.updateBatchPatchApplications),
                delta(after.updateIndividualPatchApplications, before.updateIndividualPatchApplications),
                ms(after.updatePatchConversionNanos, before.updatePatchConversionNanos),
                ms(after.updatePatchApplyNanos, before.updatePatchApplyNanos),
                ms(after.batchPatchPlanningNanos, before.batchPatchPlanningNanos),
                ms(after.batchPatchConformanceNanos, before.batchPatchConformanceNanos),
                ms(after.batchPatchBuildUpdatesNanos, before.batchPatchBuildUpdatesNanos),
                ms(after.batchPatchCommitNanos, before.batchPatchCommitNanos),
                ms(after.patchBoundaryNanos, before.patchBoundaryNanos),
                ms(after.patchGasNanos, before.patchGasNanos),
                ms(after.documentUpdateRoutingNanos, before.documentUpdateRoutingNanos));
        System.out.printf(Locale.ROOT,
                "  documentView workflowFromFrozen=%d workflowFromDocument=%d workflowMisses=%d bexMaterialized=%d frozenDirect=%d frozenRootFallback=%d undefined=%d updateMaterializeBefore=%d updateMaterializeAfter=%d%n",
                delta(after.workflowDocumentViewsFromFrozen, before.workflowDocumentViewsFromFrozen),
                delta(after.workflowDocumentViewsFromDocument, before.workflowDocumentViewsFromDocument),
                delta(after.workflowDocumentViewMisses, before.workflowDocumentViewMisses),
                delta(after.bexDocumentViewMaterializedHits, before.bexDocumentViewMaterializedHits),
                delta(after.bexDocumentViewFrozenDirectHits, before.bexDocumentViewFrozenDirectHits),
                delta(after.bexDocumentViewFrozenRootFallbackHits, before.bexDocumentViewFrozenRootFallbackHits),
                delta(after.bexDocumentViewUndefinedHits, before.bexDocumentViewUndefinedHits),
                delta(after.documentUpdateBeforeMaterializations, before.documentUpdateBeforeMaterializations),
                delta(after.documentUpdateAfterMaterializations, before.documentUpdateAfterMaterializations));
    }

    private static long delta(long after, long before) {
        return after - before;
    }

    private static double ms(long afterNanos, long beforeNanos) {
        return nanosToMs(afterNanos - beforeNanos);
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static void assertNoRootTemplates(Node document) {
        assertFalse(document.getProperties().containsKey("paynoteTemplate"));
        assertFalse(document.getProperties().containsKey("hotelOrderTemplate"));
        assertFalse(document.getProperties().containsKey("restaurantOrderTemplate"));
    }

    private static Node operationEvent(ComputeWorkflowTestSupport support,
                                       String timelineId,
                                       int timestamp,
                                       String operation,
                                       Node request) {
        return support.operationRequest(timelineId, timestamp, operation, request);
    }

    private static Node packagePaynote(ComputeWorkflowTestSupport support) {
        return support.yaml(String.join("\n",
                "name: Package PayNote",
                "packageId: weekend-badura-cud-malina",
                "status: Pending authorization",
                "amount: 499",
                "currency: PLN",
                "startDate: 2026-06-20",
                "endDate: 2026-06-21",
                "customer: Customer",
                "travelAgency: Travel Agency",
                "cardProcessor: Card Processor",
                "restaurantOrderProvided: false",
                "hotelOrderProvided: false",
                "restaurantConfirmed: false",
                "hotelConfirmed: false",
                "captureRequested: false",
                "captured: false",
                "contracts:",
                "  travelAgencyChannel:",
                "    type: Coordination/Timeline Channel",
                "    timelineId: travel-agency",
                "  cardProcessorChannel:",
                "    type: Coordination/Timeline Channel",
                "    timelineId: card-processor",
                "  confirmAuthorization:",
                "    type: Coordination/Operation",
                "    channel: cardProcessorChannel",
                "  confirmAuthorizationImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: confirmAuthorization",
                "    steps:",
                "      - name: BuildAuthorizationPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $ne:",
                "                  - $document: /status",
                "                  - Pending authorization",
                "              then:",
                "                - $fail: PayNote authorization can only be confirmed while pending",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: Authorized",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: PayNote Authorized",
                "              amount:",
                "                $document: /amount",
                "              currency:",
                "                $document: /currency",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "  provideRestaurantOrder:",
                "    type: Coordination/Operation",
                "    channel: travelAgencyChannel",
                "    request:",
                "      name: Restaurant Order",
                "      packageId: weekend-badura-cud-malina",
                "      restaurantName: Restaurant Cud Malina",
                "      description: 250zl Dinner for Two",
                "      dinnerDate: 2026-06-20",
                "      amount: 250",
                "      currency: PLN",
                "      status: Pending",
                "      contracts:",
                "        restaurantChannel:",
                "          timelineId: restaurant",
                "        confirm:",
                "          channel: restaurantChannel",
                "        confirmImpl:",
                "          operation: confirm",
                "  provideRestaurantOrderImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: provideRestaurantOrder",
                "    steps:",
                "      - name: BuildRestaurantOrderPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $ne:",
                "                  - $document: /status",
                "                  - Authorized",
                "              then:",
                "                - $fail: Restaurant Order can only be provided after PayNote authorization",
                "          - $if:",
                "              cond:",
                "                $document: /restaurantOrderProvided",
                "              then:",
                "                - $fail: Restaurant Order is already provided",
                "          - $appendChange:",
                "              op: add",
                "              path: /restaurantOrder",
                "              val:",
                "                $binding:",
                "                  name: event",
                "                  path: /message/request",
                "          - $appendChange:",
                "              op: replace",
                "              path: /restaurantOrderProvided",
                "              val: true",
                "          - $appendChange:",
                "              op: add",
                "              path: /contracts/componentOrders/paths/-",
                "              val: /restaurantOrder",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "  provideHotelOrder:",
                "    type: Coordination/Operation",
                "    channel: travelAgencyChannel",
                "    request:",
                "      name: Hotel Order",
                "      packageId: weekend-badura-cud-malina",
                "      hotelName: Hotel Badura",
                "      roomType: Deluxe Room",
                "      checkIn: 2026-06-20",
                "      checkOut: 2026-06-21",
                "      amount: 249",
                "      currency: PLN",
                "      status: Pending",
                "      contracts:",
                "        hotelChannel:",
                "          timelineId: hotel",
                "        confirm:",
                "          channel: hotelChannel",
                "        confirmImpl:",
                "          operation: confirm",
                "  provideHotelOrderImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: provideHotelOrder",
                "    steps:",
                "      - name: BuildHotelOrderPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $ne:",
                "                  - $document: /status",
                "                  - Authorized",
                "              then:",
                "                - $fail: Hotel Order can only be provided after PayNote authorization",
                "          - $if:",
                "              cond:",
                "                $document: /hotelOrderProvided",
                "              then:",
                "                - $fail: Hotel Order is already provided",
                "          - $appendChange:",
                "              op: add",
                "              path: /hotelOrder",
                "              val:",
                "                $binding:",
                "                  name: event",
                "                  path: /message/request",
                "          - $appendChange:",
                "              op: replace",
                "              path: /hotelOrderProvided",
                "              val: true",
                "          - $appendChange:",
                "              op: add",
                "              path: /contracts/componentOrders/paths/-",
                "              val: /hotelOrder",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "  componentOrders:",
                "    type: Process Embedded",
                "    paths: []",
                "  restaurantOrderEvents:",
                "    type: Embedded Node Channel",
                "    childPath: /restaurantOrder",
                "  hotelOrderEvents:",
                "    type: Embedded Node Channel",
                "    childPath: /hotelOrder",
                "  restaurantOrderConfirmed:",
                "    type: Coordination/Sequential Workflow",
                "    channel: restaurantOrderEvents",
                "    event:",
                "      type: Coordination/Event",
                "      kind: Component Order Confirmed",
                "      component: restaurant",
                "    steps:",
                "      - name: BuildRestaurantConfirmedPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /restaurantConfirmed",
                "              val: true",
                "          - $if:",
                "              cond:",
                "                $and:",
                "                  - $document: /hotelConfirmed",
                "                  - $not:",
                "                      $document: /captureRequested",
                "              then:",
                "                - $appendChange:",
                "                    op: replace",
                "                    path: /captureRequested",
                "                    val: true",
                "                - $appendEvent:",
                "                    type: Coordination/Event",
                "                    kind: PayNote Capture Requested",
                "                    amount:",
                "                      $document: /amount",
                "                    currency:",
                "                      $document: /currency",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "  hotelOrderConfirmed:",
                "    type: Coordination/Sequential Workflow",
                "    channel: hotelOrderEvents",
                "    event:",
                "      type: Coordination/Event",
                "      kind: Component Order Confirmed",
                "      component: hotel",
                "    steps:",
                "      - name: BuildHotelConfirmedPatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $appendChange:",
                "              op: replace",
                "              path: /hotelConfirmed",
                "              val: true",
                "          - $if:",
                "              cond:",
                "                $and:",
                "                  - $document: /restaurantConfirmed",
                "                  - $not:",
                "                      $document: /captureRequested",
                "              then:",
                "                - $appendChange:",
                "                    op: replace",
                "                    path: /captureRequested",
                "                    val: true",
                "                - $appendEvent:",
                "                    type: Coordination/Event",
                "                    kind: PayNote Capture Requested",
                "                    amount:",
                "                      $document: /amount",
                "                    currency:",
                "                      $document: /currency",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true",
                "  confirmCapture:",
                "    type: Coordination/Operation",
                "    channel: cardProcessorChannel",
                "  confirmCaptureImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: confirmCapture",
                "    steps:",
                "      - name: BuildCapturePatch",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $not:",
                "                  $document: /captureRequested",
                "              then:",
                "                - $fail: PayNote capture cannot be confirmed before both orders confirm",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: Captured",
                "          - $appendChange:",
                "              op: replace",
                "              path: /captured",
                "              val: true",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: PayNote Captured",
                "              amount:",
                "                $document: /amount",
                "              currency:",
                "                $document: /currency",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true"));
    }

    private static Node restaurantOrder(ComputeWorkflowTestSupport support) {
        return support.yaml(String.join("\n",
                "name: Restaurant Order",
                "packageId: weekend-badura-cud-malina",
                "restaurantName: Restaurant Cud Malina",
                "description: 250zl Dinner for Two",
                "dinnerDate: 2026-06-20",
                "amount: 250",
                "currency: PLN",
                "status: Pending",
                "contracts:",
                "  restaurantChannel:",
                "    type: Coordination/Timeline Channel",
                "    timelineId: restaurant",
                "  confirm:",
                "    type: Coordination/Operation",
                "    channel: restaurantChannel",
                "  confirmImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: confirm",
                "    steps:",
                "      - name: BuildConfirmation",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $ne:",
                "                  - $document: /status",
                "                  - Pending",
                "              then:",
                "                - $fail: Restaurant Order can only be confirmed while pending",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: Confirmed",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Component Order Confirmed",
                "              component: restaurant",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true"));
    }

    private static Node hotelOrder(ComputeWorkflowTestSupport support) {
        return support.yaml(String.join("\n",
                "name: Hotel Order",
                "packageId: weekend-badura-cud-malina",
                "hotelName: Hotel Badura",
                "roomType: Deluxe Room",
                "checkIn: 2026-06-20",
                "checkOut: 2026-06-21",
                "amount: 249",
                "currency: PLN",
                "status: Pending",
                "contracts:",
                "  hotelChannel:",
                "    type: Coordination/Timeline Channel",
                "    timelineId: hotel",
                "  confirm:",
                "    type: Coordination/Operation",
                "    channel: hotelChannel",
                "  confirmImpl:",
                "    type: Coordination/Sequential Workflow Operation",
                "    operation: confirm",
                "    steps:",
                "      - name: BuildConfirmation",
                "        type: Coordination/Compute",
                "        do:",
                "          - $if:",
                "              cond:",
                "                $ne:",
                "                  - $document: /status",
                "                  - Pending",
                "              then:",
                "                - $fail: Hotel Order can only be confirmed while pending",
                "          - $appendChange:",
                "              op: replace",
                "              path: /status",
                "              val: Confirmed",
                "          - $appendEvent:",
                "              type: Coordination/Event",
                "              kind: Component Order Confirmed",
                "              component: hotel",
                "          - $return:",
                "              changeset:",
                "                $changeset: true",
                "              events:",
                "                $events: true"));
    }

    private static void assertContainsEventKind(List<Node> events, String expectedKind) {
        for (Node event : events) {
            if (expectedKind.equals(eventKind(event))) {
                return;
            }
        }
        throw new AssertionError("Expected event kind " + expectedKind + " in " + events);
    }

    private static String eventKind(Node event) {
        if (event == null) {
            return null;
        }
        Node kind = event.getProperties() != null ? event.getProperties().get("kind") : null;
        Object value = kind != null ? kind.getValue() : null;
        return value instanceof String ? (String) value : null;
    }

    private static void assertRuntimeFatal(DocumentProcessingResult result, String expectedMessage) {
        assertEquals(ProcessorStatus.RUNTIME_FATAL, result.status(), result.failureReason());
        if (result.failureReason() != null && result.failureReason().contains(expectedMessage)) {
            return;
        }
        assertTrue(containsStringValue(result.document(), expectedMessage),
                "Expected fatal reason containing: " + expectedMessage);
    }

    private static boolean containsStringValue(Node node, String expectedMessage) {
        if (node == null) {
            return false;
        }
        Object value = node.getValue();
        if (value instanceof String && ((String) value).contains(expectedMessage)) {
            return true;
        }
        if (containsStringValue(node.getType(), expectedMessage)) {
            return true;
        }
        if (containsStringValue(node.getContracts(), expectedMessage)) {
            return true;
        }
        if (node.getItems() != null) {
            for (Node item : node.getItems()) {
                if (containsStringValue(item, expectedMessage)) {
                    return true;
                }
            }
        }
        if (node.getProperties() != null) {
            for (Node property : node.getProperties().values()) {
                if (containsStringValue(property, expectedMessage)) {
                    return true;
                }
            }
        }
        return false;
    }

}
