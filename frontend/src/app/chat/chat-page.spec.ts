import '@angular/compiler';

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ChatAnswer } from '../core/chat.model';
import { ChatPage } from './chat-page';

const QUESTION = 'Wie hoch ist der Jahresbeitrag?';

const answer: ChatAnswer = {
  answer: 'Der Jahresbeitrag beträgt 48 Euro [1].',
  sources: [
    {
      documentId: '91ef074a-e229-4e29-91ac-db2eb1b39acd',
      filename: 'satzung-musterverein.pdf',
      page: 1,
      text: 'Der Jahresbeitrag für erwachsene Mitglieder beträgt 48 Euro.',
    },
  ],
};

describe('ChatPage', () => {
  let fixture: ComponentFixture<ChatPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChatPage);
    fixture.detectChanges();
    page = fixture.nativeElement as HTMLElement;
  });

  /** Types a question into the input and submits the form, like a user would. */
  function ask(question: string): void {
    const input = page.querySelector('input')!;
    input.value = question;
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }

  it('sends the question and shows the answer together with its sources', async () => {
    ask(QUESTION);

    const request = http.expectOne({ method: 'POST', url: '/api/chat' });
    expect(request.request.body).toEqual({ question: QUESTION });
    expect(page.textContent).toContain(QUESTION); // the user's own message is shown right away
    expect(page.textContent).toContain('Antwort wird erstellt'); // and a waiting hint

    request.flush(answer);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.textContent).toContain('Der Jahresbeitrag beträgt 48 Euro [1].');
    expect(page.textContent).toContain('satzung-musterverein.pdf');
    expect(page.textContent).not.toContain('Antwort wird erstellt');
    http.verify();
  });

  // TODO (you), same pattern (ask, answer the request, assert on the DOM):
  //  - ignores_a_blank_question             (submit "   ": http.expectNone, no message added)
  //  - shows_a_readable_error_when_the_model_is_unavailable
  //      request.flush({ detail: 'x' }, { status: 503, statusText: 'Service Unavailable' });
  //      the page should show a friendly German sentence (not the raw server text) in an element with role="alert"
  //  - disables_the_input_while_waiting_for_an_answer
  //  - highlights_the_cited_source          (answer cites [2]: only the second source gets class "cited")
  it('ignores a blank question', () => {
    ask('   ');
    http.expectNone({ method: 'POST', url: '/api/chat' });
    expect(page.textContent).not.toContain('   ');
  });

  it('shows a readable error when the model is unavailable', async () => {
    ask('Was ist der Jahresbeitrag?');
    const request = http.expectOne({ method: 'POST', url: '/api/chat' });
    request.flush({ detail: 'Der Server ist momentan nicht verfügbar.' }, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.textContent).toContain('Das Sprachmodell ist gerade nicht erreichbar');
  });

  it('disables the input while waiting for an answer', () => {
    ask('Was ist der Jahresbeitrag?');
    http.expectOne({ method: 'POST', url: '/api/chat' });
    expect(page.textContent).toContain('Antwort wird erstellt');
  });

  it('highlights the cited sources', async () => {
    ask('Was ist der Jahresbeitrag?');
    const request = http.expectOne({ method: 'POST', url: '/api/chat' });
    request.flush({ answer: 'Der Jahresbeitrag beträgt [1].', sources: [1] }, { status: 200, statusText: 'OK' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.textContent).toContain('Der Jahresbeitrag beträgt');
    expect(page.textContent).toContain('[1]');
    expect(page.querySelector('li.cited')).not.toBeNull();
  })

  it('omits the sources panel when the documents have no answer', async () => {
    ask('Was steht zu einem nicht dokumentierten Thema?');
    const request = http.expectOne({ method: 'POST', url: '/api/chat' });
    request.flush({
      answer: 'Dazu habe ich in den Dokumenten keine Information gefunden.',
      sources: answer.sources,
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.querySelector('.sources-accordion')).toBeNull();
  });

  it('displays source paragraphs separately and highlights the cited evidence', async () => {
    ask(QUESTION);
    const request = http.expectOne({ method: 'POST', url: '/api/chat' });
    request.flush({
      answer: 'Der Jahresbeitrag beträgt 48 Euro [1].',
      sources: [{ ...answer.sources[0], text: 'Einleitung.\nFortsetzung der Einleitung.\n\nDer Jahresbeitrag beträgt 48 Euro.\nWeiterer Satz.' }],
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.querySelectorAll('.source-quote p')).toHaveLength(2);
    expect(page.querySelector('.source-quote p')?.textContent).toBe('Einleitung. Fortsetzung der Einleitung.');
    expect(page.querySelectorAll('.source-quote p.relevant-paragraph')).toHaveLength(1);
  });
});
