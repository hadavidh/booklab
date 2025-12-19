package com.booklab.demo.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "pages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "page_number"})
)
public class Page {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageStatus status = PageStatus.PENDING;

    @Lob
    private String ocrText;

    @Lob
    private String frText;

    private String error;

    public Long getId() { return id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public PageStatus getStatus() { return status; }
    public void setStatus(PageStatus status) { this.status = status; }

    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }

    public String getFrText() { return frText; }
    public void setFrText(String frText) { this.frText = frText; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
