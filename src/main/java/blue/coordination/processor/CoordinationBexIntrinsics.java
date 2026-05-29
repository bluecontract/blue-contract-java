package blue.coordination.processor;

import blue.bex.api.BexIntrinsicInvocation;
import blue.bex.api.BexIntrinsicProcessor;
import blue.bex.api.BexIntrinsicRegistry;
import blue.bex.value.BexValue;
import blue.bex.value.BexValues;
import blue.repo.common.CryptoEd25519Verify;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CoordinationBexIntrinsics {
    public static final long COMMON_CRYPTO_ED25519_VERIFY_GAS = 500L;

    private CoordinationBexIntrinsics() {
    }

    public static BexIntrinsicRegistry common() {
        return registerCommon(BexIntrinsicRegistry.empty());
    }

    public static BexIntrinsicRegistry registerCommon(BexIntrinsicRegistry registry) {
        BexIntrinsicRegistry base = registry != null ? registry : BexIntrinsicRegistry.empty();
        return base.with(CryptoEd25519Verify.class, commonCryptoEd25519Verify());
    }

    public static BexIntrinsicProcessor commonCryptoEd25519Verify() {
        return invocation -> {
            invocation.chargeGas(COMMON_CRYPTO_ED25519_VERIFY_GAS);
            return BexValues.scalar(verifyEd25519(invocation));
        };
    }

    private static boolean verifyEd25519(BexIntrinsicInvocation invocation) {
        String publicKeyText = textField(invocation.field("publicKey"));
        String message = textField(invocation.field("message"));
        String signatureText = textField(invocation.field("signature"));
        if (publicKeyText == null || message == null || signatureText == null) {
            return false;
        }

        byte[] publicKey = decodeBase64Url(publicKeyText, 32);
        byte[] signature = decodeBase64Url(signatureText, 64);
        if (publicKey == null || signature == null) {
            return false;
        }

        try {
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            verifier.update(messageBytes, 0, messageBytes.length);
            return verifier.verifySignature(signature);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String textField(BexValue value) {
        if (value == null
                || value.isUndefined()
                || value.isNull()
                || !"text".equals(BexValues.kind(value))) {
            return null;
        }
        return value.asText();
    }

    private static byte[] decodeBase64Url(String value, int expectedLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        int remainder = normalized.length() % 4;
        if (remainder == 1) {
            return null;
        }
        if (remainder != 0) {
            StringBuilder builder = new StringBuilder(normalized);
            for (int i = remainder; i < 4; i++) {
                builder.append('=');
            }
            normalized = builder.toString();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            return decoded.length == expectedLength ? decoded : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
