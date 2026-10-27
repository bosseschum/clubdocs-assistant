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
  styleUrls: ['./styles/chat-message.css'],
})
export class ChatMessageView {
  readonly message = input.required<ChatMessage>();

  protected readonly formattedText = computed(() => this.message().text);

  /**
   * Source numbers the answer cites. Numbers without a matching source are dropped: a small model
   * can write [7] when only four sources exist, and that must neither be counted nor highlighted.
   */
  protected readonly cited = computed(() =>
    citedNumbers(this.message().text).filter((n) => n <= this.message().sources.length),
  );
}
