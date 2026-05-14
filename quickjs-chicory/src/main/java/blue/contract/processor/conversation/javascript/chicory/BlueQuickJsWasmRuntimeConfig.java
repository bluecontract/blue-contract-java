package blue.contract.processor.conversation.javascript.chicory;

import java.nio.file.Path;

public final class BlueQuickJsWasmRuntimeConfig {
    public static final String BLUE_QUICKJS_ROOT_PROPERTY = "blue.quickjs.root";
    public static final String ENGINE_BUILD_HASH_PROPERTY = "blue.quickjs.engineBuildHash";
    public static final int DEFAULT_GAS_VERSION = 8;
    public static final String DEFAULT_EXECUTION_PROFILE = "baseline-v1";

    private final Path blueQuickJsRoot;
    private final String expectedEngineBuildHash;
    private final String expectedAbiManifestHash;
    private final int expectedGasVersion;
    private final String expectedExecutionProfile;
    private final String expectedVariant;
    private final String expectedBuildType;
    private final boolean preferClasspathResources;

    private BlueQuickJsWasmRuntimeConfig(Builder builder) {
        this.blueQuickJsRoot = builder.blueQuickJsRoot;
        this.expectedEngineBuildHash = normalizeHex(builder.expectedEngineBuildHash);
        this.expectedAbiManifestHash = normalizeHex(builder.expectedAbiManifestHash);
        this.expectedGasVersion = builder.expectedGasVersion;
        this.expectedExecutionProfile = builder.expectedExecutionProfile;
        this.expectedVariant = builder.expectedVariant;
        this.expectedBuildType = builder.expectedBuildType;
        this.preferClasspathResources = builder.preferClasspathResources;
    }

    public Path blueQuickJsRoot() {
        return blueQuickJsRoot;
    }

    public String expectedEngineBuildHash() {
        return expectedEngineBuildHash;
    }

    public String expectedAbiManifestHash() {
        return expectedAbiManifestHash;
    }

    public int expectedGasVersion() {
        return expectedGasVersion;
    }

    public String expectedExecutionProfile() {
        return expectedExecutionProfile;
    }

    public String expectedVariant() {
        return expectedVariant;
    }

    public String expectedBuildType() {
        return expectedBuildType;
    }

    public boolean preferClasspathResources() {
        return preferClasspathResources;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BlueQuickJsWasmRuntimeConfig defaultConfig() {
        return builder().build();
    }

    private static String normalizeHex(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toLowerCase();
    }

    public static final class Builder {
        private Path blueQuickJsRoot;
        private String expectedEngineBuildHash = System.getProperty(ENGINE_BUILD_HASH_PROPERTY);
        private String expectedAbiManifestHash = HostV1Manifest.HOST_V1_HASH;
        private int expectedGasVersion = DEFAULT_GAS_VERSION;
        private String expectedExecutionProfile = DEFAULT_EXECUTION_PROFILE;
        private String expectedVariant = "wasm32";
        private String expectedBuildType = "release";
        private boolean preferClasspathResources = true;

        public Builder blueQuickJsRoot(Path blueQuickJsRoot) {
            this.blueQuickJsRoot = blueQuickJsRoot;
            return this;
        }

        public Builder expectedEngineBuildHash(String expectedEngineBuildHash) {
            this.expectedEngineBuildHash = expectedEngineBuildHash;
            return this;
        }

        public Builder expectedAbiManifestHash(String expectedAbiManifestHash) {
            this.expectedAbiManifestHash = expectedAbiManifestHash;
            return this;
        }

        public Builder expectedGasVersion(int expectedGasVersion) {
            this.expectedGasVersion = expectedGasVersion;
            return this;
        }

        public Builder expectedExecutionProfile(String expectedExecutionProfile) {
            this.expectedExecutionProfile = expectedExecutionProfile;
            return this;
        }

        public Builder expectedVariant(String expectedVariant) {
            this.expectedVariant = expectedVariant;
            return this;
        }

        public Builder expectedBuildType(String expectedBuildType) {
            this.expectedBuildType = expectedBuildType;
            return this;
        }

        public Builder preferClasspathResources(boolean preferClasspathResources) {
            this.preferClasspathResources = preferClasspathResources;
            return this;
        }

        public BlueQuickJsWasmRuntimeConfig build() {
            if (expectedGasVersion <= 0) {
                throw new IllegalArgumentException("expectedGasVersion must be positive");
            }
            if (expectedExecutionProfile == null || expectedExecutionProfile.trim().isEmpty()) {
                throw new IllegalArgumentException("expectedExecutionProfile must not be blank");
            }
            if (expectedVariant == null || expectedVariant.trim().isEmpty()) {
                throw new IllegalArgumentException("expectedVariant must not be blank");
            }
            if (expectedBuildType == null || expectedBuildType.trim().isEmpty()) {
                throw new IllegalArgumentException("expectedBuildType must not be blank");
            }
            return new BlueQuickJsWasmRuntimeConfig(this);
        }
    }
}
