package com.hrgenius.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Document queries: per-employee listing, delete guard for employee records. */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByEmployeeIdOrderByUploadedAtDesc(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);
}
