package com.hrgenius.document;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.hrgenius.employee.Employee;

import lombok.Getter;
import lombok.Setter;

/**
 * Metadata for an uploaded employee document. Bytes live on disk under the
 * configured storage directory; FILE_PATH stores the stored file name only
 * (never a user-controlled path). Mapped to DOCUMENTS (V1).
 */
@Entity
@Table(name = "DOCUMENTS")
@Getter
@Setter
public class Document {

    public enum DocumentType {
        RESUME, OFFER_LETTER, ID_PROOF, CERTIFICATE, EXPERIENCE_LETTER, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EMPLOYEE_ID", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 30)
    private DocumentType documentType;

    /** Original file name as uploaded (display only). */
    @Column(name = "FILE_NAME", nullable = false)
    private String fileName;

    /** Stored file name on disk (UUID-prefixed, sanitized). */
    @Column(name = "FILE_PATH", nullable = false)
    private String filePath;

    /** Size in bytes. */
    @Column(name = "FILE_SIZE")
    private Long fileSize;

    @Column(name = "UPLOADED_AT", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime uploadedAt;
}
