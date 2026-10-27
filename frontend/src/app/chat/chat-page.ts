import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, ViewChild } from '@angular/core';

import { ChatApi } from '../core/chat-api';
import { ChatMessage } from '../core/chat.model';
import { ChatInput } from './chat-input';
import { ChatMessageView } from './chat-message';

/**
 * Container component: holds the conversation and talks to the API.
 * The two child components only display things and emit events.
 */
@Component({
  selector: 'app-chat-page',
  imports: [ChatInput, ChatMessageView],
  templateUrl: './chat-page.html',
  styleUrls: ['./styles/chat-page.css'],
})
export class ChatPage {
  private readonly api = inject(ChatApi);
  private nextId = 1;

  @ViewChild('input') protected input?: ChatInput;

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly pending = signal(false);

  protected onQuestionSubmitted(question: string): void {
    this.addMessage('user', question, [])
    this.pending.set(true)
    this.api.ask(question).subscribe({
      next: (answer) => {
        this.pending.set(false)
        this.input?.focus()
        this.addMessage('assistant', answer.answer, answer.sources)
      },
      error: (err) => {
        this.pending.set(false)
        this.input?.focus()
        this.addMessage('error', this.errorText(err))
      },
    })
    return;
  }

  private addMessage(
    role: ChatMessage['role'],
    text: string,
    sources: ChatMessage['sources'] = [],
  ): void {
    this.messages.update((list) => [...list, { id: this.nextId++, role, text, sources }]);
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
