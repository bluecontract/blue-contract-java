package blue.contract.packager.utils;

import blue.contract.packager.model.BluePackage;
import blue.language.NodeProvider;
import blue.language.model.Node;
import blue.language.preprocess.Preprocessor;
import blue.language.provider.BasicNodeProvider;
import blue.language.utils.BlueIdCalculator;
import blue.language.utils.NodeProviderWrapper;
import blue.language.utils.NodeToObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static blue.language.utils.UncheckedObjectMapper.YAML_MAPPER;

public class NodePreprocessor {
    public Node preprocess(Node node, String dirName, Map<String, BluePackage> processedPackages) {
        List<Node> packageContents = new ArrayList<>(processedPackages.values().stream()
                .map(BluePackage::getPackageContent)
                .collect(Collectors.toList()));

        NodeProvider nodeProvider = NodeProviderWrapper.wrap(new BasicNodeProvider(packageContents));
        Preprocessor preprocessor = new Preprocessor(nodeProvider);
        Node blueNode = processedPackages.get(dirName).getPackageContent();
        return preprocessor.preprocess(node, blueNode);
    }
}