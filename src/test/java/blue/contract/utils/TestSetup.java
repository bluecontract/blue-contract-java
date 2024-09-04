package blue.contract.utils;

import blue.language.Blue;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.utils.TypeClassResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static blue.contract.utils.Utils.defaultTestingEnvironment;

public class TestSetup {
    private static Blue blue;
    private static boolean isSetupComplete = false;
    private static RepositoryExportingTool exportingTool;

    public static synchronized Blue getBlue() {
        ensureSetup();
        return blue;
    }

    private static void ensureSetup() {
        if (!isSetupComplete) {
            try {
                exportingTool = new RepositoryExportingTool(defaultTestingEnvironment());
                CompletableFuture<Void> exportFuture = exportingTool.exportRepositoryAsync();

                exportFuture.get(60, TimeUnit.SECONDS);

                blue = new Blue(
                        new DirectoryBasedNodeProvider("src/main/resources/blue-preprocessed", "src/main/resources/samples"),
                        new TypeClassResolver("blue.contract.model")
                );
                isSetupComplete = true;
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException("Setup failed", e);
            }
        }
    }

    public static boolean isExportComplete() {
        return exportingTool != null && exportingTool.isExportComplete();
    }
}