package blue.coordination.processor;

import blue.language.model.Node;
import blue.repo.BlueRepository;
import blue.repo.common.CryptoEd25519Verify;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryTypeAliasPreprocessorTest {
    @Test
    void resolvesCorrectRepositoryQualifiedIntrinsicAlias() {
        Node node = intrinsicType("Common/Crypto Ed25519 Verify");

        Node resolved = new RepositoryTypeAliasPreprocessor(BlueRepository.v1_3_0()).preprocess(node);

        assertEquals(CryptoEd25519Verify.blueId(),
                resolved.getProperties().get("$intrinsic").getType().getBlueId());
    }

    @Test
    void doesNotResolveOldDoubleCommonAlias() {
        Node node = intrinsicType("Common/Common/Crypto Ed25519 Verify");

        Node resolved = new RepositoryTypeAliasPreprocessor(BlueRepository.v1_3_0()).preprocess(node);

        assertEquals("Common/Common/Crypto Ed25519 Verify",
                resolved.getProperties().get("$intrinsic").getType().getBlueId());
    }

    private static Node intrinsicType(String blueId) {
        Node intrinsic = new Node()
                .type(new Node().blueId(blueId))
                .properties("message", new Node().value("test"));
        return new Node().properties("$intrinsic", intrinsic);
    }
}
