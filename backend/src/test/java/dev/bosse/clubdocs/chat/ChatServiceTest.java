package dev.bosse.clubdocs.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Unit test for the chat orchestration. The language model is a fake lambda: tests must never
 * need Ollama, a network, or an API key.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    RetrievalService retrievalService;

    @Test
    void ask_givesTheFallbackAnswerAndNeverCallsTheModelWhenNothingWasFound() {
        when(retrievalService.retrieve("Wer ist Kassenwart?")).thenReturn(List.of());
        ChatModel chatModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("should not be called"))));
        ChatService service = new ChatService(retrievalService, ChatClient.builder(chatModel));

        ChatAnswer answer = service.ask("Wer ist Kassenwart?");

        assertEquals(PromptBuilder.NO_ANSWER_TEXT, answer.answer());
        assertTrue(answer.sources().isEmpty());
    }

    @Test
    void ask_returnsTheModelAnswerTogetherWithTheSources() {
        UUID docId = UUID.randomUUID();
        RetrievedChunk chunk = new RetrievedChunk(docId, "satzung.pdf", 3, "Die Mitgliedschaft kostet 48 Euro.");
        when(retrievalService.retrieve("Wie viel kostet die Mitgliedschaft?")).thenReturn(List.of(chunk));
        ChatModel chatModel = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("48 Euro"))));
        ChatService service = new ChatService(retrievalService, ChatClient.builder(chatModel));

        ChatAnswer answer = service.ask("Wie viel kostet die Mitgliedschaft?");

        assertEquals("48 Euro", answer.answer());
        assertEquals(List.of(chunk), answer.sources());
    }

    @Test
    void ask_sendsAPromptContainingTheQuestionAndTheChunkText() {
        UUID docId = UUID.randomUUID();
        RetrievedChunk chunk = new RetrievedChunk(docId, "protokoll.pdf", 1, "Der Verein wurde 2020 gegründet.");
        when(retrievalService.retrieve("Wann wurde der Verein gegründet?")).thenReturn(List.of(chunk));

        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel chatModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("2020"))));
        };
        ChatService service = new ChatService(retrievalService, ChatClient.builder(chatModel));

        service.ask("Wann wurde der Verein gegründet?");

        String promptText = capturedPrompt.get().getInstructions().stream()
                .map(m -> m.getText())
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(promptText.contains("Wann wurde der Verein gegründet?"));
        assertTrue(promptText.contains("Der Verein wurde 2020 gegründet."));
    }

    @Test
    void ask_throwsChatUnavailableExceptionWhenTheModelFails() {
        RetrievedChunk chunk = new RetrievedChunk(UUID.randomUUID(), "satzung.pdf", 1, "Text");
        when(retrievalService.retrieve("Frage")).thenReturn(List.of(chunk));
        ChatModel chatModel = prompt -> {
            throw new RuntimeException("connection refused");
        };
        ChatService service = new ChatService(retrievalService, ChatClient.builder(chatModel));

        assertThrows(ChatUnavailableException.class, () -> service.ask("Frage"));
    }
}
