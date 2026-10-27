import { Component, effect, inject, ViewChild } from '@angular/core';

import { ChatStore } from '../core/chat-store';
import { ChatInput } from './chat-input';
import { ChatMessageView } from './chat-message';

/**
 * Container component: displays the conversation held by the ChatStore and
 * forwards new questions to it. The store outlives this routed component, so
 * the conversation is still there after navigating away and back.
 */
@Component({
  selector: 'app-chat-page',
  imports: [ChatInput, ChatMessageView],
  templateUrl: './chat-page.html',
  styleUrls: ['./styles/chat-page.css'],
})
export class ChatPage {
  private readonly store = inject(ChatStore);

  @ViewChild('input') protected input?: ChatInput;

  protected readonly messages = this.store.messages;
  protected readonly pending = this.store.pending;

  constructor() {
    effect(() => {
      // Focus the input again once an answer has arrived (also after navigating back).
      if (!this.store.pending()) {
        this.input?.focus();
      }
    });
  }

  protected onQuestionSubmitted(question: string): void {
    this.store.ask(question);
  }
}
