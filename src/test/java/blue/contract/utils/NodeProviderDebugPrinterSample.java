package blue.contract.utils;

import blue.contract.AliasRegistry;
import blue.contract.Sample;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.preprocess.Preprocessor;
import blue.language.provider.ClasspathBasedNodeProvider;
import blue.language.provider.DirectoryBasedNodeProvider;
import blue.language.provider.SequentialNodeProvider;
import blue.language.provider.ipfs.IPFSNodeProvider;
import blue.language.utils.TypeClassResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class NodeProviderDebugPrinterSample {

    public static void main(String[] args) throws IOException {
        Blue defaultBlue = defaultBlue();
        NodeProvider nodeProvider = defaultBlue.getNodeProvider();

        Map<String, Object> blueIdToContentMap = findTargetProviderMap(nodeProvider);

        if (blueIdToContentMap != null) {
            blueIdToContentMap.forEach((key, obj) -> {
                String content = obj.toString();
                String truncatedContent = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                System.out.println(key + ": " + truncatedContent);
            });
        } else {
            System.out.println("No DirectoryBasedNodeProvider or ClasspathBasedNodeProvider found.");
        }
    }

    private static Map<String, Object> findTargetProviderMap(NodeProvider provider) {
        if (provider instanceof DirectoryBasedNodeProvider) {
            return ((DirectoryBasedNodeProvider) provider).getBlueIdToContentMap();
        } else if (provider instanceof ClasspathBasedNodeProvider) {
            return ((ClasspathBasedNodeProvider) provider).getBlueIdToContentMap();
        } else if (provider instanceof SequentialNodeProvider) {
            SequentialNodeProvider sequentialProvider = (SequentialNodeProvider) provider;
            for (NodeProvider subProvider : sequentialProvider.getNodeProviders()) {
                Map<String, Object> result = findTargetProviderMap(subProvider);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static Blue defaultBlue() {
        NodeProvider directoryBasedNodeProvider = null;
        try {
            directoryBasedNodeProvider = new DirectoryBasedNodeProvider("repository/Blue Contracts v0.4", "samples");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Blue blue = new Blue(
                new SequentialNodeProvider(
                        directoryBasedNodeProvider,
                        new IPFSNodeProvider()
                ),
                new TypeClassResolver("blue.contract.model")
        );
        blue.addPreprocessingAliases(AliasRegistry.MAP);
        return blue;
    }

}