import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { ChatApi } from './chat-api';
import { ChatMessage } from './chat.model';

/**
 * Holds the conversation for the lifetime of the app.
 *
 * `ChatPage` is a routed component and is destroyed on every navigation.
 * Keeping the messages and the in-flight request here, in a root-level
 * service, makes them survive navigating to another page and back.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly api = inject(ChatApi);
  private nextId = 1;

  private readonly messageList = signal<ChatMessage[]>([]);
  readonly messages = this.messageList.asReadonly();

  private readonly pendingState = signal(false);
  readonly pending = this.pendingState.asReadonly();

  /** Sends a question and appends the answer (or a readable error) when it arrives. */
  ask(question: string): void {
    this.addMessage('user', question);
    this.pendingState.set(true);
    this.api.ask(question).subscribe({
      next: (answer) => {
        this.pendingState.set(false);
        this.addMessage('assistant', answer.answer, answer.sources);
      },
      error: (err) => {
        this.pendingState.set(false);
        this.addMessage('error', this.errorText(err));
      },
    });
  }

  private addMessage(
    role: ChatMessage['role'],
    text: string,
    sources: ChatMessage['sources'] = [],
  ): void {
    this.messageList.update((list) => [...list, { id: this.nextId++, role, text, sources }]);
  }

  private errorText(err: HttpErrorResponse): string {
    if (err.status === 503) {
      return "'Das Sprachmodell ist gerade nicht erreichbar. Bitte versuche es später noch einmal.";
    } else if (err.status === 400) {
      return typeof err.error.detail === 'string' ? err.error.detail : 'Die Frage wurde abgelehnt';
    } else if (err.status === 0) {
      return 'Der Server ist nicht erreichbar.';
    }
    return 'Ein unbekannter Fehler ist aufgetreten';
  }
}
