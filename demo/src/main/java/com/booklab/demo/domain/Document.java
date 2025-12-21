package com.booklab.demo.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents")
public class Document {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status = DocumentStatus.UPLOADED;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() { this.updatedAt = Instant.now(); }

  public Long getId() { return id; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public DocumentStatus getStatus() { return status; }
  public void setStatus(DocumentStatus status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
