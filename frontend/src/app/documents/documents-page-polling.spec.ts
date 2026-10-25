import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, it, vi } from 'vitest';

import { DocumentResponse, DocumentStatus } from '../core/document.model';
import { DocumentsPage } from './documents-page';

const POLL_INTERVAL_MS = 2000;

function documentWith(status: DocumentStatus): DocumentResponse {
  return {
    id: '685b158f-f91f-431d-8e43-84a5b277cd77',
    filename: 'bylaws.pdf',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    status,
    uploadedAt: '2026-10-16T09:38:59.756016Z',
  };
}

/**
 * Polling is time-based, so these tests use fake timers: advancing the clock is instant.
 *
 * Reminder: never `await fixture.whenStable()` while a request is unanswered (it would hang).
 * Advance the fake clock instead, then render, then answer the request.
 */
describe('DocumentsPage polling', () => {
  let fixture: ComponentFixture<DocumentsPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [DocumentsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DocumentsPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Moves the fake clock forward and lets Angular process the result. */
  async function advance(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  /** Answers the pending GET /api/documents with one document in the given status. */
  async function answerWith(status: DocumentStatus): Promise<void> {
    http.expectOne('/api/documents').flush([documentWith(status)]);
    await advance(0);
  }

  it('keeps polling while a document is processing and stops once it is READY', async () => {
    await answerWith('PROCESSING'); // initial load

    await advance(POLL_INTERVAL_MS); // poll 1
    await answerWith('PROCESSING');

    await advance(POLL_INTERVAL_MS); // poll 2: only happens if polling continues after a reload
    await answerWith('READY');

    await advance(10 * POLL_INTERVAL_MS); // nothing pending any more
    http.expectNone('/api/documents');
    http.verify();
  });

  it('does not poll at all when every document is already finished', async () => {
    await answerWith('READY');

    await advance(10 * POLL_INTERVAL_MS);

    http.expectNone('/api/documents');
    http.verify();
  });

  it('does not poll when the list failed to load', async () => {
    http.expectOne('/api/documents').flush([], { status: 500, statusText: 'Error' });
    await advance(0);
    http.verify();
  });

  it('polls after a new upload makes a document pending', async () => {
    await answerWith('UPLOADED');
    await advance(0);
    await answerWith('PROCESSING');
    await advance(POLL_INTERVAL_MS);
    await answerWith('READY');
    await advance(10 * POLL_INTERVAL_MS);
    http.expectNone('/api/documents');
    http.verify();
  });
});
