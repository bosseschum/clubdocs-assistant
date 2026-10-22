import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DocumentResponse } from './document.model';

/**
 * Everything the frontend knows about the backend's /api/documents endpoints.
 * Components never build URLs themselves.
 */
@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);

  /**
   * The document list as a reactive resource. It loads automatically and exposes
   * value(), isLoading(), error() and reload() as signals/methods.
   * Call documents.reload() after an upload or delete to refresh it.
   */
  readonly documents = httpResource<DocumentResponse[]>(() => '/api/documents', {
    defaultValue: [],
  });

  upload(file: File): Observable<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file); // must match @RequestParam("file") in the controller
    return this.http.post<DocumentResponse>('/api/documents', formData);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`/api/documents/${id}`);
  }
}
