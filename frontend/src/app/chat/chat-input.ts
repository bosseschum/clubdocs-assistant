import { Component, input, output } from '@angular/core';

/**
 * Presentational component: a text field and a button. It reports the question upwards and
 * knows nothing about HTTP.
 */
@Component({
  selector: 'app-chat-input',
  templateUrl: './chat-input.html',
  styleUrls: ['./styles/chat-input.css'],
})
export class ChatInput {
  readonly disabled = input(false);
  readonly questionSubmitted = output<string>();

  protected onSubmit(event: Event, field: HTMLInputElement): void {
    event.preventDefault();
    const question = field.value.trim();
    if (question.length === 0) {
      return
    }
    this.questionSubmitted.emit(question);
    field.value = '';
  }
}
