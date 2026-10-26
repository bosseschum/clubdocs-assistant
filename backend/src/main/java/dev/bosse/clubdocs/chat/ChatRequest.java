package dev.bosse.clubdocs.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/chat. Validated by @Valid in the controller (a violation gives HTTP 400). */
public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 500, message = "question must be at most 500 characters")
        String question) {}
