/** A piece of a document that the answer was based on (mirrors the backend's RetrievedChunk). */
export interface SourceChunk {
  documentId: string;
  filename: string;
  page: number;
  text: string;
}

/** Response of POST /api/chat (mirrors the backend's ChatAnswer). */
export interface ChatAnswer {
  answer: string;
  sources: SourceChunk[];
}

export type ChatRole = 'user' | 'assistant' | 'error';

/** One entry in the conversation shown on the page. */
export interface ChatMessage {
  id: number;
  role: ChatRole;
  text: string;
  /** Only assistant messages carry sources. */
  sources: SourceChunk[];
}
