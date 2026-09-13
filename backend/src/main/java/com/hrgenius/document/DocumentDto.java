package com.hrgenius.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mirrors the document module's API payloads (Phase 11). */
public final class DocumentDto {

    private DocumentDto() {
    }

    /** Upload request — always multipart/form-data with a `file` part. */
    public record UploadRequest(
            @NotNull Long employeeId,
            @NotBlank @Size(max = 30) String documentType) {
    }

    /** Metadata shown in the UI; the file itself is fetched via /download. */
    public record DocumentResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String documentType,
            String fileName,
            Long fileSize,
            String uploadedAt) {
    }
}
