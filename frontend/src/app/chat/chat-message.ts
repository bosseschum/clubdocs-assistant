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

  protected readonly visibleSources = computed(() =>
    this.message().text.trim() === 'Dazu habe ich in den Dokumenten keine Information gefunden.'
      ? []
      : this.message().sources,
  );

  /**
   * Source numbers the answer cites. Numbers without a matching source are dropped: a small model
   * can write [7] when only four sources exist, and that must neither be counted nor highlighted.
   */
  protected readonly cited = computed(() =>
    citedNumbers(this.message().text).filter((n) => n <= this.visibleSources().length),
  );

  protected paragraphs(text: string): string[] {
    return (text ?? '')
      .split(/\r?\n\s*\r?\n+/)
      .map((paragraph) => paragraph.replace(/\s*\r?\n\s*/g, ' ').trim())
      .filter(Boolean);
  }

  protected isRelevantParagraph(sourceNumber: number, paragraph: string, sourceText: string): boolean {
    if (!this.cited().includes(sourceNumber)) return false;
    const answerTerms = new Set(
      this.message().text.toLocaleLowerCase().replace(/\[\d+\]/g, '').match(/[\p{L}\d]{3,}/gu) ?? [],
    );
    const paragraphs = this.paragraphs(sourceText);
    const score = (text: string) =>
      (text.toLocaleLowerCase().match(/[\p{L}\d]{3,}/gu) ?? []).filter((term) => answerTerms.has(term)).length;
    const bestScore = Math.max(...paragraphs.map(score));
    return bestScore > 0 && score(paragraph) === bestScore;
  }
}
