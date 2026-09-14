package com.hrgenius.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.hrgenius.common.ApiResponse;
import com.hrgenius.common.PageResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Document API (Phase 11). Reads and uploads: ADMIN/HR/MANAGER;
 * delete: ADMIN/HR only. MANAGER is deliberately not allowed to upload.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<DocumentDto.DocumentResponse>>> list(
            @RequestParam Long employeeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.of(service.list(employeeId, search, page, size)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<ApiResponse<DocumentDto.DocumentResponse>> upload(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        DocumentDto.DocumentResponse response = service.upload(file, employeeId, documentType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Document uploaded", response));
    }

    /** Streams the stored bytes with the original file name. */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id) throws IOException {
        DocumentService.Download download = service.download(id);
        Path path = download.path();
        String encoded = java.net.URLEncoder.encode(download.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        StreamingResponseBody body = out -> Files.copy(path, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .contentLength(Files.size(path))
                .body(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
