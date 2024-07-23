package blue.contract.processor;

import org.graalvm.polyglot.io.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

public class CustomFileSystem implements FileSystem {
    private final FileSystem defaultFS;

    public CustomFileSystem(FileSystem defaultFS) {
        this.defaultFS = defaultFS;
    }

    @Override
    public Path parsePath(URI uri) {
        if (uri.getScheme().equals("blue")) {
            // Convert 'blue:123456' to a path to chess.js
            return Paths.get("src", "main", "resources", "samples/chess.js");
        }
        return defaultFS.parsePath(uri);
    }

    @Override
    public Path parsePath(String path) {
        if (path.startsWith("blue:")) {
            // Convert 'blue:123456' to a path to chess.js
            return Paths.get("src", "main", "resources", "samples/chess.js");
        }
        return defaultFS.parsePath(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return defaultFS.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return defaultFS.newDirectoryStream(dir, filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        defaultFS.createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        defaultFS.delete(path);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        defaultFS.copy(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        defaultFS.move(source, target, options);
    }

    @Override
    public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
        return defaultFS.isSameFile(path1, path2, options);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        defaultFS.checkAccess(path, modes, linkOptions);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return defaultFS.readAttributes(path, attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        defaultFS.setAttribute(path, attribute, value, options);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return defaultFS.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return defaultFS.toRealPath(path, linkOptions);
    }

    @Override
    public String getSeparator() {
        return defaultFS.getSeparator();
    }
}