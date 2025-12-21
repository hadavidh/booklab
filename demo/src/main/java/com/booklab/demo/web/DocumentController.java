package com.booklab.demo.web;

import com.booklab.demo.domain.*;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import com.booklab.demo.service.ProcessingService;
import com.booklab.demo.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Controller
public class DocumentController {

  private final DocumentRepository documentRepo;
  private final PageRepository pageRepo;
  private final StorageService storage;
  private final ProcessingService processing;

  public DocumentController(DocumentRepository documentRepo,
                            PageRepository pageRepo,
                            StorageService storage,
                            ProcessingService processing) {
    this.documentRepo = documentRepo;
    this.pageRepo = pageRepo;
    this.storage = storage;
    this.processing = processing;
  }

  @GetMapping("/")
  public String home(Model model) {
    model.addAttribute("documents", documentRepo.findAll().stream()
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .toList());
    return "home";
  }

  @PostMapping("/documents")
  public String createDocument(@RequestParam("title") String title,
                               @RequestParam("files") MultipartFile[] files) throws Exception {
    if (files == null || files.length == 0) return "redirect:/";

    Document doc = new Document();
    doc.setTitle(title == null || title.isBlank() ? "Document (hébreu → français)" : title.trim());
    doc = documentRepo.save(doc);

    MultipartFile[] sorted = Arrays.stream(files)
        .filter(f -> f != null && !f.isEmpty())
        .sorted((a, b) -> String.valueOf(a.getOriginalFilename())
            .compareToIgnoreCase(String.valueOf(b.getOriginalFilename())))
        .toArray(MultipartFile[]::new);

    int pageNumber = 1;
    for (MultipartFile f : sorted) {
      String relativePath = storage.savePageImage(doc.getId(), pageNumber, f);

      Page p = new Page();
      p.setDocument(doc);
      p.setPageNumber(pageNumber);
      p.setInputType(PageInputType.IMAGE);
      p.setImagePath(relativePath);
      pageRepo.save(p);

      pageNumber++;
    }

    return "redirect:/documents/" + doc.getId();
  }

  @PostMapping("/documents/{id}/add-text")
  public String addTextPage(@PathVariable Long id,
                            @RequestParam("hebrewText") String hebrewText) {
    Document doc = documentRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

    int next = pageRepo.findByDocumentIdOrderByPageNumberAsc(id).size() + 1;

    Page p = new Page();
    p.setDocument(doc);
    p.setPageNumber(next);
    p.setInputType(PageInputType.TEXT);
    p.setHebrewInputText(hebrewText == null ? "" : hebrewText.trim());
    pageRepo.save(p);

    return "redirect:/documents/" + id;
  }

  @PostMapping("/documents/{id}/process")
  public String startProcessing(@PathVariable Long id) {
    processing.processDocument(id);
    return "redirect:/documents/" + id;
  }

  @GetMapping("/documents/{id}")
  public String viewDocument(@PathVariable Long id, Model model) {
    Document doc = documentRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

    var pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(id);

    long total = pages.size();
    long done = pages.stream().filter(p -> p.getStatus() == PageStatus.DONE).count();
    long failed = pages.stream().filter(p -> p.getStatus() == PageStatus.FAILED).count();

    model.addAttribute("doc", doc);
    model.addAttribute("pages", pages);
    model.addAttribute("totalPages", total);
    model.addAttribute("donePages", done);
    model.addAttribute("failedPages", failed);

    return "document";
  }

  @GetMapping("/pages/{pageId}")
  public String viewPage(@PathVariable Long pageId, Model model) {
    Page p = pageRepo.findById(pageId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));

    model.addAttribute("page", p);
    model.addAttribute("docId", p.getDocument().getId());
    return "page";
  }
}
