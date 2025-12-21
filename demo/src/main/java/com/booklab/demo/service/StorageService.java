package com.booklab.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

@Service
public class StorageService {

  private final Path root;

  public StorageService(@Value("${app.storage.root}") String rootDir) {
    this.root = Paths.get(rootDir).toAbsolutePath().normalize();
  }

  public String savePageImage(Long docId, int pageNumber, MultipartFile file) throws IOException {
    Files.createDirectories(root);

    String docFolder = "doc-" + docId;
    Path docDir = root.resolve(docFolder);
    Files.createDirectories(docDir);

    String ext = guessExt(file.getOriginalFilename());
    String filename = String.format(Locale.ROOT, "page-%03d.%s", pageNumber, ext);
    Path target = docDir.resolve(filename).normalize();

    try (var in = file.getInputStream()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    return docFolder + "/" + filename;
  }

  public Path resolveAbsolute(String relativePath) {
    return root.resolve(relativePath).toAbsolutePath().normalize();
  }

  private String guessExt(String name) {
    if (name == null) return "jpg";
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) return "png";
    if (lower.endsWith(".webp")) return "webp";
    if (lower.endsWith(".jpeg")) return "jpeg";
    if (lower.endsWith(".jpg")) return "jpg";
    return "jpg";
  }
}
