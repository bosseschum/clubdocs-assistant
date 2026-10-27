import '@angular/compiler';

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ChatAnswer } from '../core/chat.model';
import { ChatPage } from './chat-page';

const QUESTION = 'Wie hoch ist der Jahresbeitrag?';

const answer: ChatAnswer = {
  answer: 'Der Jahresbeitrag beträgt 48 Euro.',
  sources: [],
};

describe('ChatPage state across navigation', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('keeps the conversation when the page is destroyed and recreated', async () => {
    const first = createPage();
    ask(first, QUESTION);

    http.expectOne({ method: 'POST', url: '/api/chat' }).flush(answer);
    await first.whenStable();
    first.detectChanges();
    expect(first.nativeElement.textContent).toContain(answer.answer);

    // Navigating to another route destroys the chat page component.
    first.destroy();

    const second = createPage();
    expect(second.nativeElement.textContent).toContain(QUESTION);
    expect(second.nativeElement.textContent).toContain(answer.answer);
  });

  function createPage(): ComponentFixture<ChatPage> {
    const fixture = TestBed.createComponent(ChatPage);
    fixture.detectChanges();
    return fixture;
  }

  function ask(fixture: ComponentFixture<ChatPage>, question: string): void {
    const page = fixture.nativeElement as HTMLElement;
    page.querySelector('input')!.value = question;
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }
});
