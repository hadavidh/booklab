package com.booklab.demo.web;

import com.booklab.demo.domain.Document;
import com.booklab.demo.domain.Page;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import com.booklab.demo.service.StorageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.booklab.demo.service.ProcessingService;


import java.util.Arrays;

@Controller
public class DocumentController {

    private final DocumentRepository documentRepo;
    private final PageRepository pageRepo;
    private final StorageService storage;
    private final ProcessingService processing;


    public DocumentController(DocumentRepository documentRepo, PageRepository pageRepo, StorageService storage, ProcessingService processing) {
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

        // Crée le document
        Document doc = new Document();
        doc.setTitle(title == null || title.isBlank() ? "Document (hébreu → français)" : title.trim());
        doc = documentRepo.save(doc);

        // Tri par nom de fichier (page001.jpg -> ordre correct)
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
            p.setImagePath(relativePath);
            pageRepo.save(p);

            pageNumber++;
        }

        return "redirect:/documents/" + doc.getId();
    }

    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id, Model model) {
      Document doc = documentRepo.findById(id).orElseThrow();
      var pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(id);
      long total = pages.size();
      long translated = pages.stream().filter(p -> p.getStatus() == com.booklab.demo.domain.PageStatus.TRANSLATED).count();
      long failed = pages.stream().filter(p -> p.getStatus() == com.booklab.demo.domain.PageStatus.FAILED).count();

       model.addAttribute("doc", doc);
       model.addAttribute("pages", pages);
       model.addAttribute("totalPages", total);
       model.addAttribute("translatedPages", translated);
       model.addAttribute("failedPages", failed);

       return "document";
    }

    @PostMapping("/documents/{id}/process")
    public String startProcessing(@PathVariable Long id) {
       processing.processDocument(id); // async
       return "redirect:/documents/" + id;
    }

    @GetMapping("/pages/{pageId}")
    public String viewPage(@PathVariable Long pageId, Model model) {
       Page p = pageRepo.findById(pageId).orElseThrow();
       model.addAttribute("page", p);
       model.addAttribute("docId", p.getDocument().getId());
       return "page";
    }
}