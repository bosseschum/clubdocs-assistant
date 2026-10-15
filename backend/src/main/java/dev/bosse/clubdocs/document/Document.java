package dev.bosse.clubdocs.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    // Required by Hibernate (it creates instances via reflection)
    protected Document() {}

    public Document(String filename, String contentType, long sizeBytes) {
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.UPLOADED;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public DocumentStatus getStatus() { return status; }
    public Instant getUploadedAt() { return uploadedAt; }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
}