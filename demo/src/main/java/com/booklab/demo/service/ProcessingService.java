package com.booklab.demo.service;

import com.booklab.demo.domain.*;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ProcessingService {

    private final DocumentRepository documentRepo;
    private final PageRepository pageRepo;
    private final StorageService storage;
    private final OcrService ocr;
    private final OpenAiTranslationService translator;

    public ProcessingService(DocumentRepository documentRepo,
                             PageRepository pageRepo,
                             StorageService storage,
                             OcrService ocr,
                             OpenAiTranslationService translator) {
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
        this.storage = storage;
        this.ocr = ocr;
        this.translator = translator;
    }

    @Async
    public void processDocument(Long documentId) {
        Document doc = documentRepo.findById(documentId).orElseThrow();

        if (doc.getStatus() == DocumentStatus.PROCESSING) return;

        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepo.save(doc);

        List<Page> pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(documentId);

        boolean anyFailed = false;

        for (Page p : pages) {
            if (p.getStatus() == PageStatus.TRANSLATED) continue;

            try {
                // OCR
                Path img = storage.resolveAbsolute(p.getImagePath());
                String heb = ocr.extractText(img);
                p.setOcrText(heb);
                p.setStatus(PageStatus.OCR_DONE);
                p.setError(null);
                pageRepo.save(p);

                // Traduction
                String fr = translator.translateHebrewToFrench(heb);
                p.setFrText(fr);
                p.setStatus(PageStatus.TRANSLATED);
                pageRepo.save(p);

            } catch (Exception e) {
                anyFailed = true;
                p.setStatus(PageStatus.FAILED);
                p.setError(e.getMessage());
                pageRepo.save(p);
            }
        }

        doc.setStatus(anyFailed ? DocumentStatus.FAILED : DocumentStatus.DONE);
        documentRepo.save(doc);
    }
}
