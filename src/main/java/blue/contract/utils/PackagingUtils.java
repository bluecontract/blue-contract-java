package blue.contract.utils;

import blue.contract.packager.BluePackageExporter;
import blue.contract.packager.graphbuilder.ClasspathDependencyGraphBuilder;
import blue.contract.packager.model.BluePackage;
import blue.language.Blue;
import blue.language.NodeProvider;
import blue.language.merge.NodeResolver;
import blue.language.model.Node;
import blue.language.provider.BasicNodeProvider;
import blue.language.provider.SequentialNodeProvider;
import blue.language.utils.NodeProviderWrapper;
import blue.language.utils.TypeClassResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PackagingUtils {

    public static ClasspathBasedPackagingEnvironment createClasspathBasedPackagingEnvironment(String repositoryPath, String typeClassResolverPackage) {
        return createClasspathBasedPackagingEnvironment(repositoryPath, typeClassResolverPackage, null);
    }

    public static ClasspathBasedPackagingEnvironment createClasspathBasedPackagingEnvironment(String repositoryPath, String typeClassResolverPackage,
                                                                                              List<NodeProvider> additionalNodeProviders) {
        ClasspathDependencyGraphBuilder cpBuilder = new ClasspathDependencyGraphBuilder(PackagingUtils.class.getClassLoader());
        List<BluePackage> packages = null;
        try {
            packages = BluePackageExporter.exportPackages(repositoryPath, cpBuilder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, BluePackage> packageMap = packages.stream()
                .collect(Collectors.toMap(BluePackage::getDirectoryName, pkg -> pkg));

        List<Node> preprocessedNodes = packages.stream()
                .flatMap(pkg -> pkg.getPreprocessedNodes().values().stream())
                .toList();
        List<Node> packageContents = packages.stream()
                .map(BluePackage::getPackageContent)
                .toList();

        List<Node> nodes = new ArrayList<>();
        nodes.addAll(preprocessedNodes);
        nodes.addAll(packageContents);

        BasicNodeProvider basicNodeProvider = new BasicNodeProvider(nodes);
        NodeProvider provider = NodeProviderWrapper.wrap(basicNodeProvider);
        if (additionalNodeProviders != null && !additionalNodeProviders.isEmpty()) {
            List<NodeProvider> providers = new ArrayList<>();
            providers.add(provider);
            providers.addAll(additionalNodeProviders);
            provider = new SequentialNodeProvider(providers);
        }
        Blue blue = new Blue(provider, new TypeClassResolver(typeClassResolverPackage));

        return new ClasspathBasedPackagingEnvironment(blue, packageMap);
    }

    public static class ClasspathBasedPackagingEnvironment {
        private final Blue blue;
        private final Map<String, BluePackage> packageMap;

        private ClasspathBasedPackagingEnvironment(Blue blue, Map<String, BluePackage> packageMap) {
            this.blue = blue;
            this.packageMap = packageMap;
        }

        public Blue getBlue() {
            return blue;
        }

        public BluePackage getPackage(String packageName) {
            return packageMap.get(packageName);
        }
    }
}