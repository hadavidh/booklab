package com.booklab.demo.repo;

import com.booklab.demo.domain.Page;
import com.booklab.demo.domain.PageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Long> {
    List<Page> findByDocumentIdOrderByPageNumberAsc(Long documentId);

    List<Page> findTop50ByStatusOrderByIdAsc(PageStatus status);
}