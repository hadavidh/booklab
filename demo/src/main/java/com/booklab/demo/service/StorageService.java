package com.booklab.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;

@Service
public class StorageService {

    private final Path root;

    public StorageService(@Value("${app.storage.root:data}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create storage root: " + this.root, e);
        }
    }

    public String savePageImage(Long docId, int pageNumber, MultipartFile file) throws IOException {
        String folder = "doc-" + docId;
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);

        String filename = "page-" + pageNumber + getSafeExt(file.getOriginalFilename());
        Path dest = dir.resolve(filename);

        try (var in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        return folder + "/" + filename;
    }

    public String saveDocumentPdf(Long docId, byte[] pdfBytes) throws IOException {
        String folder = "doc-" + docId;
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);

        String filename = "export.pdf";
        Path dest = dir.resolve(filename);
        Files.write(dest, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return folder + "/" + filename;
    }

    public Path resolvePath(String relativePath) {
        return root.resolve(relativePath).normalize();
    }

    public Resource loadAsResource(String relativePath) {
        try {
            Path file = resolvePath(relativePath);
            if (!Files.exists(file)) return null;
            UrlResource res = new UrlResource(file.toUri());
            return res.exists() ? res : null;
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static String getSafeExt(String original) {
        if (original == null) return ".bin";
        String lower = original.toLowerCase();
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        return ".bin";
    }
}
