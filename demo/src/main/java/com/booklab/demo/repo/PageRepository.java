package com.booklab.demo.repo;

import com.booklab.demo.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByDocumentIdOrderByPageNumberAsc(Long documentId);

    @Query("select p from Page p join fetch p.document d where p.id = :id")
    Optional<Page> findWithDocumentById(@Param("id") Long id);

    @Query("select coalesce(max(p.pageNumber), 0) from Page p where p.document.id = :docId")
    int maxPageNumber(@Param("docId") Long docId);
}
