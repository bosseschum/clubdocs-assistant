package dev.bosse.clubdocs.chat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the HTTP layer of the chat endpoint only: validation, JSON shape, error mapping.
 * The service is a mock, so no model, no database and no Docker are involved.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @Test
    void ask_returnsTheAnswerTogetherWithItsSources() throws Exception {
        RetrievedChunk source = new RetrievedChunk(
                UUID.randomUUID(), "satzung.pdf", 1, "Der Jahresbeitrag beträgt 48 Euro.");
        when(chatService.ask("Wie hoch ist der Jahresbeitrag?"))
                .thenReturn(new ChatAnswer("Der Jahresbeitrag beträgt 48 Euro [1].", List.of(source)));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Wie hoch ist der Jahresbeitrag?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Der Jahresbeitrag beträgt 48 Euro [1]."))
                .andExpect(jsonPath("$.sources[0].filename").value("satzung.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value(1));
    }

    @Test
    void ask_rejectsABlankQuestionWith400_andNeverCallsTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void ask_returns503WhenTheModelIsUnavailable() throws Exception {
        when(chatService.ask(anyString()))
                .thenThrow(new ChatUnavailableException("The language model is currently unavailable.", null));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Wer ist der Kassenwart?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The language model is currently unavailable."));
    }

    void ask_rejectsAQuestionLongerThan500Characters() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    void ask_rejectsARequestWithoutQuestionField() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}