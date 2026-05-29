package blue.coordination.processor.compute;

import blue.bex.api.BexEngine;
import blue.coordination.processor.CoordinationBexIntrinsics;
import blue.coordination.processor.CoordinationProcessorOptions;
import blue.language.model.Node;
import blue.language.processor.DocumentProcessingResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Ed25519IntrinsicWorkflowTest {
    private static final String HOTEL_DOCUMENT = "coordination/compute/ed25519-hotel-access.yaml";
    private static final String THRESHOLD_DOCUMENT = "coordination/compute/ed25519-threshold-approval.yaml";

    private static final String HOTEL_SIGNATURE =
            "oVqYjDGViWObQDdAAyiwfauZqPIt3PwzJTbdt2VbS6hdSquw8GqQyTFE-9RUf3ubupP_h35sLlZPXulrPmeeBQ";
    private static final String ALICE_SIGNATURE =
            "r2xDuEqbtGNwiDOULGp6Epc1g3L_59k-tVbh7_qnLKd1XiVkjeVCm2b7b-6HdRRATK6S0zpc_DnInvFdV3BOCw";
    private static final String BOB_SIGNATURE =
            "3EXsrtb4nLC37E14iOsREFhFgibnIl6MyYjzAztnUfpNdicSqs3lj4RTHM0N9E8uNCPufItDDxkL4Q8dzem3DQ";

    @Test
    void hotelAccessUsesCommonEd25519IntrinsicToGrantValidSignedRequest() {
        ComputeWorkflowTestSupport support = supportWithCommonIntrinsics();
        Node document = support.initialize(support.yamlResource(HOTEL_DOCUMENT)).document();

        DocumentProcessingResult result = support.process(document,
                support.operationRequest("hotel", 1, "checkIn", hotelRequest()));

        assertFalse(result.capabilityFailure(), result.failureReason());
        assertEquals(Boolean.TRUE, result.document().get("/usedNonces/customerA/hotel-nonce-1"));
        assertEquals("Hotel Access Granted", onlyEvent(result).get("/kind"));
        assertEquals("customerA", onlyEvent(result).get("/userId"));
        assertEquals("R123", onlyEvent(result).get("/reservationId"));
    }

    @Test
    void thresholdApprovalExecutesActionAfterTwoValidEd25519Approvals() {
        ComputeWorkflowTestSupport support = supportWithCommonIntrinsics();
        Node document = support.initialize(support.yamlResource(THRESHOLD_DOCUMENT)).document();

        DocumentProcessingResult afterAlice = support.process(document,
                support.operationRequest("admin", 1, "approveAction",
                        approvalRequest("alice", "alice-nonce-1", ALICE_SIGNATURE)));

        assertFalse(afterAlice.capabilityFailure(), afterAlice.failureReason());
        assertEquals("Admin Approval Recorded", onlyEvent(afterAlice).get("/kind"));
        assertEquals(Boolean.TRUE, afterAlice.document().get("/approvals/delete-file-123/alice"));

        DocumentProcessingResult afterBob = support.process(afterAlice.document(),
                support.operationRequest("admin", 2, "approveAction",
                        approvalRequest("bob", "bob-nonce-1", BOB_SIGNATURE)));

        assertFalse(afterBob.capabilityFailure(), afterBob.failureReason());
        assertEquals("Admin Action Executed", onlyEvent(afterBob).get("/kind"));
        assertEquals(Boolean.TRUE, afterBob.document().get("/approvals/delete-file-123/alice"));
        assertEquals(Boolean.TRUE, afterBob.document().get("/approvals/delete-file-123/bob"));
        assertEquals(Boolean.TRUE, afterBob.document().get("/executed/delete-file-123"));
    }

    private static ComputeWorkflowTestSupport supportWithCommonIntrinsics() {
        BexEngine engine = BexEngine.builder()
                .intrinsics(CoordinationBexIntrinsics.common())
                .build();
        return ComputeWorkflowTestSupport.create(CoordinationProcessorOptions.builder()
                .bexEngine(engine)
                .build());
    }

    private static Node hotelRequest() {
        return object(
                "userId", "customerA",
                "reservationId", "R123",
                "nonce", "hotel-nonce-1",
                "expires", 1000,
                "signature", HOTEL_SIGNATURE);
    }

    private static Node approvalRequest(String signer, String nonce, String signature) {
        return object(
                "actionId", "delete-file-123",
                "action", "delete-file",
                "resource", "file123",
                "signer", signer,
                "nonce", nonce,
                "expires", 1000,
                "signature", signature);
    }

    private static Node object(Object... fields) {
        Node node = new Node();
        for (int i = 0; i < fields.length; i += 2) {
            node.properties(String.valueOf(fields[i]), new Node().value(fields[i + 1]));
        }
        return node;
    }

    private static Node onlyEvent(DocumentProcessingResult result) {
        assertEquals(1, result.triggeredEvents().size());
        return result.triggeredEvents().get(0);
    }
}
