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
  private final OpenAiResponsesService openai;

  public ProcessingService(DocumentRepository documentRepo,
                           PageRepository pageRepo,
                           StorageService storage,
                           OpenAiResponsesService openai) {
    this.documentRepo = documentRepo;
    this.pageRepo = pageRepo;
    this.storage = storage;
    this.openai = openai;
  }

  @Async
  public void processDocument(Long documentId) {
    Document doc = documentRepo.findById(documentId).orElseThrow();

    if (doc.getStatus() == DocumentStatus.PROCESSING) return;

    doc.setStatus(DocumentStatus.PROCESSING);
    documentRepo.save(doc);

    List<Page> pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(documentId);
    boolean anyFailed = false;

    try {
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
            Path img = storage.resolveAbsolute(p.getImagePath());
            res = openai.extractNikudAndTranslateFromImage(img);
          }

          p.setHebrewPlain(res.hebrewPlain());
          p.setHebrewNikud(res.hebrewNikud());
          p.setFrText(res.french());
          p.setStatus(PageStatus.DONE);
          pageRepo.save(p);

        } catch (Throwable t) {
          anyFailed = true;
          p.setStatus(PageStatus.FAILED);
          p.setError(shortMsg(t));
          pageRepo.save(p);
        }
      }
    } finally {
      doc.setStatus(anyFailed ? DocumentStatus.DONE_WITH_ERRORS : DocumentStatus.DONE);
      documentRepo.save(doc);
    }
  }

  private static String shortMsg(Throwable t) {
    String m = t.getMessage();
    if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
    int max = 1200;
    return m.length() > max ? m.substring(0, max) : m;
  }
}
