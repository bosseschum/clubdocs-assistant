package dev.bosse.clubdocs.chat;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Answers a question from the uploaded documents: retrieve, build the prompt, ask the model.
 *
 * The model sits behind Spring AI's ChatClient. Which model it is (a local Qwen via Ollama, or a
 * hosted one later) is a matter of configuration; this class does not know.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;

    // Spring provides a ChatClient.Builder already configured for the active model.
    public ChatService(RetrievalService retrievalService, ChatClient.Builder chatClientBuilder) {
        this.retrievalService = retrievalService;
        this.chatClient = chatClientBuilder.build();
    }

    public ChatAnswer ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be null or blank");
        }

        // Retrieve ONCE: every call embeds the question and searches the vector store again, and the
        // sources shown to the user must be exactly the chunks the prompt was built from.
        List<RetrievedChunk> chunks = retrievalService.retrieve(question);
        if (chunks.isEmpty()) {
            return new ChatAnswer(PromptBuilder.NO_ANSWER_TEXT, List.of());
        }

        // Built outside the try block: an exception here is a bug in our code, and it should not be
        // reported to the user as "the model is unavailable".
        String prompt = PromptBuilder.build(question, chunks);

        String answer;
        try {
            answer = chatClient.prompt().user(prompt).call().content();
        } catch (Exception ex) {
            // The details (host, port, stack trace) belong in the log, not in the HTTP response.
            log.error("Model call failed", ex);
            throw new ChatUnavailableException(
                    "The language model is currently unavailable. Please try again later.", ex);
        }

        if (answer == null || answer.isBlank()) {
            log.warn("Model returned an empty answer (question length {})", question.length());
            throw new ChatUnavailableException("The language model returned no answer. Please try again.", null);
        }

        return new ChatAnswer(answer.strip(), chunks);
    }
}