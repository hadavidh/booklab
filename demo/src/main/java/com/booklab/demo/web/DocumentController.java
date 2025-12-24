package com.booklab.demo.web;

import com.booklab.demo.domain.*;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import com.booklab.demo.service.PdfExportService;
import com.booklab.demo.service.ProcessingService;
import com.booklab.demo.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class DocumentController {

    private final DocumentRepository documentRepo;
    private final PageRepository pageRepo;
    private final StorageService storage;
    private final ProcessingService processing;
    private final PdfExportService pdfExport;

    public DocumentController(DocumentRepository documentRepo,
                              PageRepository pageRepo,
                              StorageService storage,
                              ProcessingService processing,
                              PdfExportService pdfExport) {
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
        this.storage = storage;
        this.processing = processing;
        this.pdfExport = pdfExport;
    }

    @GetMapping("/")
    public String home(Model model,
                       @ModelAttribute("msg") String msg,
                       @ModelAttribute("err") String err) {
        model.addAttribute("docs", documentRepo.findAllByOrderByCreatedAtDesc());
        model.addAttribute("msg", (msg != null && !msg.isBlank()) ? msg : null);
        model.addAttribute("err", (err != null && !err.isBlank()) ? err : null);
        return "home";
    }

    @PostMapping("/documents")
    public String createDocument(@RequestParam String title,
                                 @RequestParam("files") MultipartFile[] files,
                                 RedirectAttributes ra) {
        try {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Titre requis");
            if (files == null || files.length == 0) throw new IllegalArgumentException("Au moins 1 image requise");

            Document doc = new Document();
            doc.setTitle(title.trim());
            doc.setStatus(DocumentStatus.UPLOADED);
            doc = documentRepo.save(doc);

            // tri stable par nom
            List<MultipartFile> sorted = Arrays.stream(files)
                    .sorted(Comparator.comparing(f -> f.getOriginalFilename() == null ? "" : f.getOriginalFilename()))
                    .toList();

            int pageNumber = 1;
            for (MultipartFile f : sorted) {
                String rel = storage.savePageImage(doc.getId(), pageNumber, f);

                Page p = new Page();
                p.setDocument(doc);
                p.setPageNumber(pageNumber);
                p.setInputType(PageInputType.IMAGE);
                p.setStatus(PageStatus.PENDING);
                p.setImagePath(rel);
                pageRepo.save(p);

                pageNumber++;
            }

            ra.addFlashAttribute("msg", "Document créé (#" + doc.getId() + ")");
            return "redirect:/documents/" + doc.getId();

        } catch (Exception e) {
            ra.addFlashAttribute("err", "Création échouée: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/documents/{id}")
    public String viewDocument(@PathVariable Long id, Model model,
                               @ModelAttribute("msg") String msg,
                               @ModelAttribute("err") String err) {
        Document doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        List<Page> pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(id);

        long total = pages.size();
        long done = pages.stream().filter(p -> p.getStatus() == PageStatus.DONE).count();
        long failed = pages.stream().filter(p -> p.getStatus() == PageStatus.FAILED).count();

        model.addAttribute("doc", doc);
        model.addAttribute("pages", pages);
        model.addAttribute("total", total);
        model.addAttribute("done", done);
        model.addAttribute("failed", failed);
        model.addAttribute("pdfReady", doc.getPdfPath() != null && !doc.getPdfPath().isBlank());
        model.addAttribute("msg", (msg != null && !msg.isBlank()) ? msg : null);
        model.addAttribute("err", (err != null && !err.isBlank()) ? err : null);

        return "document";
    }

    @PostMapping("/documents/{id}/process")
    public String startProcessing(@PathVariable Long id, RedirectAttributes ra) {
        processing.processDocument(id);
        ra.addFlashAttribute("msg", "Traitement OPEN AI lancé (asynchrone). Rafraîchis la page.");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/add-text")
    public String addTextPage(@PathVariable Long id,
                              @RequestParam("hebrewInputText") String hebrewInputText,
                              RedirectAttributes ra) {
        Document doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        if (hebrewInputText == null || hebrewInputText.isBlank()) {
            ra.addFlashAttribute("err", "Texte hébreu requis");
            return "redirect:/documents/" + id;
        }

        int next = pageRepo.maxPageNumber(id) + 1;

        Page p = new Page();
        p.setDocument(doc);
        p.setPageNumber(next);
        p.setInputType(PageInputType.TEXT);
        p.setStatus(PageStatus.PENDING);
        p.setHebrewInputText(hebrewInputText);
        pageRepo.save(p);

        ra.addFlashAttribute("msg", "Page TEXT ajoutée (#" + p.getId() + "). Tu peux l'éditer.");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/documents/{id}/pdf")
    public String generatePdf(@PathVariable Long id, RedirectAttributes ra) {
        try {
            pdfExport.generatePdfForDocument(id);
            ra.addFlashAttribute("msg", "PDF généré / régénéré.");
        } catch (Exception e) {
            ra.addFlashAttribute("err", "PDF: " + e.getMessage());
        }
        return "redirect:/documents/" + id;
    }

    @GetMapping("/documents/{id}/pdf")
    @ResponseBody
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) {
        Document doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        if (doc.getPdfPath() == null || doc.getPdfPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF non généré");
        }

        Resource res = storage.loadAsResource(doc.getPdfPath());
        if (res == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF introuvable");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document-" + id + ".pdf\"")
                .body(res);
    }
}
