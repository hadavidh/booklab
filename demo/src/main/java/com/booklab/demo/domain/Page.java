package com.booklab.demo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Document document;

    @Column(nullable = false)
    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageInputType inputType = PageInputType.IMAGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageStatus status = PageStatus.PENDING;

    // IMAGE: chemin relatif (ex: doc-3/page-1.jpg)
    private String imagePath;

    // TEXT: texte hebreu fourni
    @Lob
    private String hebrewInputText;

    // résultats (longs)
    @Lob
    private String hebrewPlain;

    @Lob
    private String hebrewNikud;

    @Lob
    private String frText;

    @Column(length = 4000)
    private String error;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public PageInputType getInputType() { return inputType; }
    public void setInputType(PageInputType inputType) { this.inputType = inputType; }

    public PageStatus getStatus() { return status; }
    public void setStatus(PageStatus status) { this.status = status; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getHebrewInputText() { return hebrewInputText; }
    public void setHebrewInputText(String hebrewInputText) { this.hebrewInputText = hebrewInputText; }

    public String getHebrewPlain() { return hebrewPlain; }
    public void setHebrewPlain(String hebrewPlain) { this.hebrewPlain = hebrewPlain; }

    public String getHebrewNikud() { return hebrewNikud; }
    public void setHebrewNikud(String hebrewNikud) { this.hebrewNikud = hebrewNikud; }

    public String getFrText() { return frText; }
    public void setFrText(String frText) { this.frText = frText; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
