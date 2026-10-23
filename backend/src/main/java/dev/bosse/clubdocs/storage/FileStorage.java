package dev.bosse.clubdocs.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Keeps uploaded files on the local disk.
 *
 * Files are named by the document's UUID, never by the uploaded filename. That
 * avoids
 * name collisions and path traversal ("../../etc/passwd"): the user cannot
 * influence the path.
 * The directory comes from the property clubdocs.storage.dir (see
 * application.yml).
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${clubdocs.storage.dir:./data/uploads}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void store(UUID id, MultipartFile file) {
        try {
            Files.createDirectories(root);
            file.transferTo(pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file for document " + id, e);
        }
    }

    public Resource load(UUID id) {
        return new FileSystemResource(pathFor(id));
    }

    /** Deleting a file that does not exist is not an error. */
    public void delete(UUID id) {
        try {
            Files.deleteIfExists(pathFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file for document " + id, e);
        }
    }

    private Path pathFor(UUID id) {
        return root.resolve(id.toString());
    }
}