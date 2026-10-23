package dev.bosse.clubdocs.ingestion;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.bosse.clubdocs.document.DocumentDeletedEvent;
import dev.bosse.clubdocs.document.DocumentUploadedEvent;

/**
 * Connects document events to ingestion.
 *
 * AFTER_COMMIT matters: the event is published inside DocumentService's
 * transaction. If ingestion
 * started immediately, a background thread could look for the document row
 * before it is committed
 * and not find it. With AFTER_COMMIT the listener only runs once the row is
 * safely in the database
 * (and not at all if the transaction rolls back).
 *
 * @Async runs the work on another thread, so the HTTP request does not wait for
 *        it.
 */
@Component
public class IngestionListener {

    private final IngestionService ingestionService;

    public IngestionListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        ingestionService.ingest(event.documentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        ingestionService.deleteChunks(event.documentId());
    }
}