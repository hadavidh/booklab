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

  // IMAGE: chemin fichier (nullable si TEXT)
  private String imagePath;

  // TEXT: texte hébreu fourni (nullable si IMAGE)
  @Lob
  private String hebrewInputText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PageInputType inputType = PageInputType.IMAGE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PageStatus status = PageStatus.PENDING;

  // Sorties OpenAI
  @Lob
  private String hebrewPlain;

  @Lob
  private String hebrewNikud;

  @Lob
  private String frText;

  // erreur longue possible -> CLOB
  @Lob
  private String error;

  public Long getId() { return id; }

  public Document getDocument() { return document; }
  public void setDocument(Document document) { this.document = document; }

  public int getPageNumber() { return pageNumber; }
  public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

  public String getImagePath() { return imagePath; }
  public void setImagePath(String imagePath) { this.imagePath = imagePath; }

  public String getHebrewInputText() { return hebrewInputText; }
  public void setHebrewInputText(String hebrewInputText) { this.hebrewInputText = hebrewInputText; }

  public PageInputType getInputType() { return inputType; }
  public void setInputType(PageInputType inputType) { this.inputType = inputType; }

  public PageStatus getStatus() { return status; }
  public void setStatus(PageStatus status) { this.status = status; }

  public String getHebrewPlain() { return hebrewPlain; }
  public void setHebrewPlain(String hebrewPlain) { this.hebrewPlain = hebrewPlain; }

  public String getHebrewNikud() { return hebrewNikud; }
  public void setHebrewNikud(String hebrewNikud) { this.hebrewNikud = hebrewNikud; }

  public String getFrText() { return frText; }
  public void setFrText(String frText) { this.frText = frText; }

  public String getError() { return error; }
  public void setError(String error) { this.error = error; }
}
