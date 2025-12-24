package com.booklab.demo.service;

import com.booklab.demo.domain.*;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private final DocumentRepository documentRepo;
    private final PageRepository pageRepo;
    private final StorageService storage;
    private final OpenAiResponsesService openai;
    private final PdfExportService pdfExport;

    public ProcessingService(DocumentRepository documentRepo,
                             PageRepository pageRepo,
                             StorageService storage,
                             OpenAiResponsesService openai,
                             PdfExportService pdfExport) {
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
        this.storage = storage;
        this.openai = openai;
        this.pdfExport = pdfExport;
    }

    @Async
    public void processDocument(Long documentId) {
        Document doc = documentRepo.findById(documentId).orElseThrow();

        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            log.info("Document {} déjà en PROCESSING -> skip", documentId);
            return;
        }

        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepo.save(doc);

        List<Page> pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(documentId);
        boolean anyFailed = false;

        for (Page p : pages) {
            if (p.getStatus() == PageStatus.DONE) continue;

            p.setStatus(PageStatus.PROCESSING);
            p.setError(null);
            pageRepo.save(p);

            try {
                OpenAiResponsesService.ExtractTranslateResult res;

                if (p.getInputType() == PageInputType.TEXT) {
                    String heb = p.getHebrewInputText();
                    if (heb == null || heb.isBlank()) {
                        throw new IllegalStateException("TEXT page without hebrewInputText");
                    }
                    res = openai.nikudAndTranslateFromText(heb);
                } else {
                    if (p.getImagePath() == null || p.getImagePath().isBlank()) {
                        throw new IllegalStateException("IMAGE page without imagePath");
                    }
                    Path img = storage.resolvePath(p.getImagePath());
                    res = openai.extractTranslateFromImage(img);
                }

                p.setHebrewPlain(res.hebrewPlain());
                p.setHebrewNikud(res.hebrewNikud());
                p.setFrText(res.frText());
                p.setStatus(PageStatus.DONE);
                p.setError(null);
                pageRepo.save(p);

            } catch (Exception e) {
                anyFailed = true;
                p.setStatus(PageStatus.FAILED);
                p.setError(shortMsg(e));
                pageRepo.save(p);
                log.warn("Page {} FAILED: {}", p.getId(), e.getMessage());
            }
        }

        doc.setStatus(anyFailed ? DocumentStatus.DONE_WITH_ERRORS : DocumentStatus.DONE);
        documentRepo.save(doc);

        // Génère/regen PDF à la fin (si tu veux)
        try {
            pdfExport.generatePdfForDocument(documentId);
        } catch (Exception e) {
            log.warn("PDF generation failed for doc {}: {}", documentId, e.getMessage());
            doc.setStatus(DocumentStatus.DONE_WITH_ERRORS);
            documentRepo.save(doc);
        }
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        m = m.replaceAll("\\s+", " ").trim();
        return m.length() > 900 ? m.substring(0, 900) + "..." : m;
    }
}
