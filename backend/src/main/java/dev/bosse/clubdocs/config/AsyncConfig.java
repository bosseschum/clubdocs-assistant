package dev.bosse.clubdocs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on @Async. Methods marked @Async run on a background thread pool, so an
 * upload
 * request returns immediately while the slow ingestion (PDF parsing,
 * embeddings) continues.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}