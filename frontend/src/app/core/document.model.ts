/** Mirrors the backend's DocumentStatus enum. */
export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';

/** Mirrors the backend's DocumentResponse record (JSON as sent by Spring). */
export interface DocumentResponse {
  id: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  status: DocumentStatus;
  /** ISO 8601 timestamp, e.g. "2026-10-16T09:38:59.756016Z" */
  uploadedAt: string;
}