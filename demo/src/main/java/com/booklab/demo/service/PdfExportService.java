package com.booklab.demo.service;

import com.booklab.demo.domain.Document;
import com.booklab.demo.domain.Page;
import com.booklab.demo.repo.DocumentRepository;
import com.booklab.demo.repo.PageRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);

    private final DocumentRepository documentRepo;
    private final PageRepository pageRepo;
    private final StorageService storage;

    public PdfExportService(DocumentRepository documentRepo, PageRepository pageRepo, StorageService storage) {
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
        this.storage = storage;
    }

    public void generatePdfForDocument(Long documentId) throws Exception {
        log.info("PDF: start generation for document {}", documentId);

        Document doc = documentRepo.findById(documentId).orElseThrow();
        List<Page> pages = pageRepo.findByDocumentIdOrderByPageNumberAsc(documentId);

        String xhtml = buildXhtml(doc, pages);

        byte[] hebFont = loadFontBytes("fonts/NotoSansHebrew-Regular.ttf");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 256)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);

            if (hebFont != null) {
                builder.useFont(() -> new ByteArrayInputStream(hebFont), "NotoSansHebrew");
                log.info("PDF: Hebrew font loaded");
            } else {
                log.warn("PDF: Hebrew font NOT found in classpath: /fonts/NotoSansHebrew-Regular.ttf");
            }

            builder.toStream(out);
            builder.run();

            byte[] pdfBytes = out.toByteArray();
            String rel = storage.saveDocumentPdf(documentId, pdfBytes);
            doc.setPdfPath(rel);
            documentRepo.save(doc);

            log.info("PDF: done for document {} -> {}", documentId, rel);
        }
    }

    private static byte[] loadFontBytes(String cp) {
        try (InputStream in = new ClassPathResource(cp).getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildXhtml(Document doc, List<Page> pages) {
        StringBuilder sb = new StringBuilder(64_000);
        sb.append("<!DOCTYPE html>")
          .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"fr\">")
          .append("<head>")
          .append("<meta charset=\"utf-8\" />")
          .append("<title>").append(escapeXml(doc.getTitle())).append("</title>")
          .append("<style>")
          .append("body{font-family:Arial, sans-serif; font-size:12px;}")
          .append(".page{page-break-after:always; padding:18px;}")
          .append(".h{font-size:16px; font-weight:bold; margin-bottom:10px;}")
          .append(".he{font-family:'NotoSansHebrew', Arial; direction:rtl; unicode-bidi:bidi-override; text-align:right; white-space:pre-wrap; font-size:14px; line-height:1.7;}")
          .append(".fr{white-space:pre-wrap; margin-top:10px; font-size:12px; line-height:1.6;}")
          .append(".sep{margin-top:14px; border-top:1px solid #ddd;}")
          .append("</style>")
          .append("</head><body>");

        int idx = 1;
        for (Page p : pages) {
            String he = (p.getHebrewNikud() != null && !p.getHebrewNikud().isBlank())
                    ? p.getHebrewNikud()
                    : (p.getHebrewPlain() != null ? p.getHebrewPlain() : "");

            String fr = (p.getFrText() != null ? p.getFrText() : "");

            sb.append("<div class=\"page\">")
              .append("<div class=\"h\">Page ").append(idx++).append("</div>")
              .append("<div class=\"he\" dir=\"rtl\">").append(escapeXml(he)).append("</div>")
              .append("<div class=\"sep\"></div>")
              .append("<div class=\"fr\">").append(escapeXml(fr)).append("</div>")
              .append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
