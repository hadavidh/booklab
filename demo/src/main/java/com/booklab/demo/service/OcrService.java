package com.booklab.demo.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class OcrService {

    private final String tessdataPath;
    private final String language;

    public OcrService(
            @Value("${app.ocr.tessdata-path}") String tessdataPath,
            @Value("${app.ocr.language}") String language
    ) {
        this.tessdataPath = tessdataPath;
        this.language = language;
    }

    public String extractText(Path imagePath) {
        try {
            Tesseract t = new Tesseract();
            t.setDatapath(tessdataPath);   // .../tessdata
            t.setLanguage(language);       // "heb"
            // Optionnel: t.setPageSegMode(1);

            String txt = t.doOCR(imagePath.toFile());
            return txt == null ? "" : txt.trim();
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }
}
