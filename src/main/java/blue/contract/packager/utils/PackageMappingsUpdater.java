package blue.contract.packager.utils;

import blue.contract.packager.model.BluePackage;
import blue.language.model.Node;
import blue.language.utils.BlueIdCalculator;

import java.util.HashMap;
import java.util.Map;

public class PackageMappingsUpdater {
    public void update(String dirName, String nodeName, Node preprocessedNode, Map<String, BluePackage> processedPackages) {
        String blueId = BlueIdCalculator.calculateBlueId(preprocessedNode);
        BluePackage bluePackage = processedPackages.get(dirName);
        Node packageContent = bluePackage.getPackageContent();
        Node packageMappingsNode = packageContent.getItems().get(1);

        if (packageMappingsNode.getProperties() == null) {
            packageMappingsNode.properties(new HashMap<>());
        }

        Node mappingsNode = packageMappingsNode.getProperties().computeIfAbsent("mappings", k -> new Node());

        if (mappingsNode.getProperties() == null) {
            mappingsNode.properties(new HashMap<>());
        }

        mappingsNode.getProperties().put(nodeName, new Node().value(blueId));

        bluePackage.getMappings().put(nodeName, blueId);
    }
}