package blue.contract.utils;

import blue.language.NodeProvider;
import blue.language.provider.ClasspathBasedNodeProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static blue.contract.utils.PackagingUtils.createClasspathBasedPackagingEnvironment;

public class BlueExporter {

    public static final String SAMPLES_DIR = "samples";
    public static final String REPOSITORY_DIR = "blue-repository";

    public static PackagingUtils.ClasspathBasedPackagingEnvironment defaultTestingEnvironment() throws IOException {
        List<NodeProvider> additionalNodeProviders = Collections.singletonList(new ClasspathBasedNodeProvider(SAMPLES_DIR));
        return createClasspathBasedPackagingEnvironment(REPOSITORY_DIR, "blue.contract.model", additionalNodeProviders);
    }

    public static void main(String[] args) throws Exception {
        RepositoryExportingTool exportingTool = new RepositoryExportingTool(defaultTestingEnvironment());
        exportingTool.exportRepository();
    }
}