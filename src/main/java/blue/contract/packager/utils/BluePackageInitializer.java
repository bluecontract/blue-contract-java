package blue.contract.packager.utils;

import blue.contract.packager.model.BluePackage;
import blue.language.model.Node;
import blue.language.utils.BlueIdCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static blue.contract.packager.BluePackageExporter.*;

public class BluePackageInitializer {
    public BluePackage initialize(String dirName, String dependency, Map<String, BluePackage> processedPackages) {
        List<Node> packageItems = new ArrayList<>();

        String blueId = dependency.equals(ROOT_DEPENDENCY) ? BOOTSTRAP_BLUE_ID : calculateDependencyBlueId(dependency, processedPackages);
        packageItems.add(new Node().blueId(blueId));

        Node typeNode = new Node().type(new Node().blueId(REPLACE_INLINE_TYPES_WITH_BLUE_ID_TRANSFORMER_BLUE_ID));
        packageItems.add(typeNode);

        Node packageContent = new Node().items(packageItems);
        return new BluePackage(dirName, packageContent);
    }

    private String calculateDependencyBlueId(String dependency, Map<String, BluePackage> processedPackages) {
        BluePackage dependencyPackage = processedPackages.get(dependency);

        if (dependencyPackage == null) {
            throw new IllegalStateException("Dependency package not found: " + dependency);
        }

        Node dependencyContent = dependencyPackage.getPackageContent();
        return BlueIdCalculator.calculateBlueId(dependencyContent.getItems());
    }
}