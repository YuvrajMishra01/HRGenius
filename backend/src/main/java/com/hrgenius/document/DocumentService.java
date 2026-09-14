package com.hrgenius.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hrgenius.common.Lists;
import com.hrgenius.common.PageResponse;
import com.hrgenius.employee.Employee;
import com.hrgenius.employee.EmployeeRepository;

/**
 * Document storage + metadata (Phase 11). Bytes are written under
 * {@code app.documents.storage-dir} with UUID-prefixed, sanitized names so
 * original names and paths can never escape the directory. Validation:
 * allowed document types, extension whitelist, size cap.
 */
@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png");
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DocumentRepository repository;
    private final EmployeeRepository employees;
    private final Path storageDir;

    public DocumentService(DocumentRepository repository, EmployeeRepository employees,
                           org.springframework.core.env.Environment env) {
        this.repository = repository;
        this.employees = employees;
        this.storageDir = Paths.get(env.getProperty("app.documents.storage-dir", "./uploads/documents"))
                .toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<DocumentDto.DocumentResponse> list(Long employeeId, String search,
                                                           Integer page, Integer size) {
        String term = Lists.cleanSearch(search);
        List<DocumentDto.DocumentResponse> rows = repository.findByEmployeeIdOrderByUploadedAtDesc(employeeId)
                .stream()
                .map(this::toResponse)
                .filter(Lists.containsTerm(
                        r -> r.documentType() + " " + (r.fileName() == null ? "" : r.fileName()), term))
                .toList();
        return Lists.page(rows, Lists.cleanPage(page), Lists.cleanSize(size));
    }

    // -------------------------------------------------------------- upload

    @Transactional
    public DocumentDto.DocumentResponse upload(MultipartFile file, Long employeeId, String documentTypeRaw) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 5 MB limit");
        }
        Document.DocumentType documentType = parseType(documentTypeRaw);
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + employeeId));

        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type ." + extension + " is not allowed. Permitted: "
                    + String.join(", ", ALLOWED_EXTENSIONS.stream().sorted().toList()));
        }

        try {
            Files.createDirectories(storageDir);
            String storedName = UUID.randomUUID() + "-" + sanitize(original);
            Path target = storageDir.resolve(storedName).normalize();
            // Defense in depth: resolved path must stay inside the storage dir.
            if (!target.startsWith(storageDir)) {
                throw new IllegalArgumentException("Invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Document document = new Document();
            document.setEmployee(employee);
            document.setDocumentType(documentType);
            document.setFileName(original);
            document.setFilePath(storedName);
            document.setFileSize(file.getSize());
            Document saved = repository.save(document);
            return toResponse(saved);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the uploaded file", e);
        }
    }

    // ------------------------------------------------------------ download

    @Transactional(readOnly = true)
    public Download download(Long id) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        Path file = storageDir.resolve(document.getFilePath()).normalize();
        if (!file.startsWith(storageDir) || !Files.exists(file)) {
            throw new EntityNotFoundException("Stored file is missing: " + document.getFileName());
        }
        String mediaType = switch (extensionOf(document.getFileName())) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
        return new Download(document.getFileName(), mediaType, file);
    }

    /** File bytes plus the metadata needed for Content-Disposition. */
    public record Download(String fileName, String mediaType, Path path) {
    }

    // -------------------------------------------------------------- delete

    @Transactional
    public void delete(Long id) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        repository.delete(document);
        try {
            Files.deleteIfExists(storageDir.resolve(document.getFilePath()).normalize());
        } catch (IOException e) {
            // Metadata is gone; a stale file on disk must not fail the request.
        }
    }

    // ------------------------------------------------------------- helpers

    private DocumentDto.DocumentResponse toResponse(Document document) {
        Employee employee = document.getEmployee();
        return new DocumentDto.DocumentResponse(
                document.getId(),
                employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getEmployeeCode(),
                document.getDocumentType().name(),
                document.getFileName(),
                document.getFileSize(),
                document.getUploadedAt() == null ? null : document.getUploadedAt().format(TS));
    }

    private Document.DocumentType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Document type is required");
        }
        try {
            return Document.DocumentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown document type: " + raw
                    + ". Allowed: RESUME, OFFER_LETTER, ID_PROOF, CERTIFICATE, EXPERIENCE_LETTER, OTHER");
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips everything except word chars, dots, and dashes; caps length. */
    private String sanitize(String original) {
        String base = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 60) {
            String ext = extensionOf(base);
            base = base.substring(0, Math.max(1, 60 - ext.length() - 1)) + (ext.isEmpty() ? "" : "." + ext);
        }
        return base;
    }
}
