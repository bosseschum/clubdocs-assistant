import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DocumentResponse } from '../core/document.model';
import { DocumentsPage } from './documents-page';
import { DocumentUpload } from './document-upload';
import { By } from '@angular/platform-browser';

const sampleDocument: DocumentResponse = {
  id: '685b158f-f91f-431d-8e43-84a5b277cd77',
  filename: 'bylaws.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  status: 'UPLOADED',
  uploadedAt: '2026-10-16T09:38:59.756016Z',
};

describe('DocumentsPage', () => {
  let fixture: ComponentFixture<DocumentsPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DocumentsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DocumentsPage);
  });

  afterEach(() => {
    // Fails the test if the component made a request the test did not expect.
    http.verify();
    vi.restoreAllMocks();
  });

  /** Renders the page and answers the initial GET /api/documents. */
  async function renderWithDocuments(documents: DocumentResponse[]): Promise<HTMLElement> {
    fixture.detectChanges();
    http.expectOne('/api/documents').flush(documents);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the documents returned by the backend', async () => {
    const page = await renderWithDocuments([sampleDocument]);

    expect(page.textContent).toContain('bylaws.pdf');
  });

  it('shows a hint when there are no documents', async () => {
    const page = await renderWithDocuments([]);

    expect(page.textContent).toContain('No documents yet');
  });

  it('shows an error message when loading fails', async () => {
    fixture.detectChanges();
    http.expectOne('/api/documents').flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Could not load documents');
  });

  it ('does nothing when user cancels the delete confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    const page = await renderWithDocuments([sampleDocument]);
    page.querySelector('button')!.click();

    http.expectNone({ method: 'DELETE', url: '/api/documents/' + sampleDocument.id });
  });

  it('calls the API and reloads the list when user confirms deletion', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const page = await renderWithDocuments([sampleDocument]);
    page.querySelector('button')!.click();

    http.expectOne({ method: 'DELETE', url: '/api/documents/' + sampleDocument.id }).flush(null);
    fixture.detectChanges();   // the reload GET is issued now (no whenStable before flushing it!)
    http.expectOne('/api/documents').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.textContent).toContain('No documents yet');
  });

  it ('shows the server message when upload fails with 400', async () => {
    const page = await renderWithDocuments([sampleDocument]);

    const upload = fixture.debugElement.query(By.directive(DocumentUpload))
      .componentInstance as DocumentUpload;
    upload.fileSelected.emit(new File([''], 'empty.txt'));

    http.expectOne({ method: 'POST', url: '/api/documents' })
      .flush({ detail: 'File is empty' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    fixture.detectChanges();

    const alert = page.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('File is empty');
  });
});