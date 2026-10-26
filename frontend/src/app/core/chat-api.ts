import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ChatAnswer } from './chat.model';

/** Everything the frontend knows about the backend's /api/chat endpoint. */
@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);

  ask(question: string): Observable<ChatAnswer> {
    return this.http.post<ChatAnswer>('/api/chat', { question });
  }
}
