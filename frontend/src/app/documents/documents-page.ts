import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';

import { DocumentApi } from '../core/document-api';
import { DocumentList } from './document-list';
import { DocumentUpload } from './document-upload';

/**
 * Container component: talks to the API and holds the page state.
 * The two child components only display things and emit events.
 */
@Component({
  selector: 'app-documents-page',
  imports: [DocumentUpload, DocumentList],
  templateUrl: './documents-page.html',
})
export class DocumentsPage {
  private readonly api = inject(DocumentApi);

  protected readonly documents = this.api.documents;
  protected readonly hasPendingDocuments = computed(() => {
    if (this.documents.status() === 'error') {
      return false;
    }
    return this.documents
      .value()
      .some((document) => document.status === 'UPLOADED' || document.status === 'PROCESSING');
  });
  protected readonly uploading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  private reloadScheduled = false;

  constructor() {
    effect((onCleanup) => {
      const status = this.documents.status();
      const hasPending = this.hasPendingDocuments();

      if (status !== 'resolved' || !hasPending) {
        this.reloadScheduled = false;
        return;
      }

      if (this.reloadScheduled) {
        return;
      }

      const docs = this.documents.value();
      const hasJustUploaded = docs.some((d) => d.status === 'UPLOADED');

      if (hasJustUploaded) {
        this.reloadScheduled = true;
        // Reset reloadScheduled asynchronously or immediately depending on cycle,
        // but for immediate reload:
        this.documents.reload();
        // Clear flag after reload trigger so subsequent polls can run
        queueMicrotask(() => {
          this.reloadScheduled = false;
        });
        return;
      }

      this.reloadScheduled = true;
      const timer = setTimeout(() => {
        this.reloadScheduled = false;
        this.documents.reload();
      }, 2000);

      onCleanup(() => clearTimeout(timer));
    });
  }

  protected onFileSelected(file: File): void {
    this.errorMessage.set(null);
    this.uploading.set(true);

    this.api.upload(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.documents.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        this.errorMessage.set(this.uploadErrorMessage(err));
      },
    });
  }

  protected onDeleteRequested(id: string): void {
    if (!window.confirm('Are you sure you want to delete this document?')) {
      return;
    }

    this.errorMessage.set(null);

    this.api.remove(id).subscribe({
      next: () => this.documents.reload(),
      error: () => this.errorMessage.set('Failed to delete document.'),
    });
  }

  private uploadErrorMessage(err: HttpErrorResponse): string {
    if (err.status === 413) {
      return 'File is too large.';
    }
    // The backend answers 400 with a problem+json body whose "detail" explains why (e.g., empty file).
    if (err.status === 400 && typeof err.error?.detail === 'string') {
      return err.error.detail;
    }
    return 'Failed to upload file.';
  }
}
