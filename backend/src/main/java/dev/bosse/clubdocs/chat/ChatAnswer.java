package dev.bosse.clubdocs.chat;

import java.util.List;

/**
 * What the chat endpoint returns: the model's answer and the chunks it was based on, so the UI
 * can show "source: satzung.pdf, page 2" under each answer.
 *
 * (Named ChatAnswer, not ChatResponse: Spring AI already has a class called ChatResponse.)
 */
public record ChatAnswer(String answer, List<RetrievedChunk> sources) {}
