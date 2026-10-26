import { Component, computed, input } from '@angular/core';

import { ChatMessage } from '../core/chat.model';
import { citedNumbers } from './citations';

/**
 * Presentational component: one message in the conversation. For answers it lists the sources
 * and highlights the ones the answer actually cited as [1], [2], ...
 */
@Component({
  selector: 'app-chat-message',
  templateUrl: './chat-message.html',
  styles: `
    .text { white-space: pre-wrap; margin: 0.25rem 0; }
    .user .text { font-weight: 600; }
    .error .text { color: #b00020; }
    li.cited { font-weight: 600; }
    li:not(.cited) { opacity: 0.7; }
    blockquote { margin: 0.25rem 0 0.75rem 0; white-space: pre-wrap; }
  `,
})
export class ChatMessageView {
  readonly message = input.required<ChatMessage>();

  protected readonly cited = computed(() => citedNumbers(this.message().text));
  protected readonly formattedText = computed(() =>
    this.message().text.replace(/\[(\d+)\]/g, 'siehe [$1]'),
  );
}
