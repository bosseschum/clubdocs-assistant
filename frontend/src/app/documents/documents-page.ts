import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

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
  protected readonly uploading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

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
    // The backend answers 400 with a problem+json body whose "detail" explains why (e.g. empty file).
    if (err.status === 400 && typeof err.error?.detail === 'string') {
      return err.error.detail;
    }
    return 'Failed to upload file.';
  }
}