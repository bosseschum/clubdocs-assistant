package dev.bosse.clubdocs.chat;

/** Thrown when the language model cannot be reached or fails (for example Ollama is not running). */
public class ChatUnavailableException extends RuntimeException {

    public ChatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
