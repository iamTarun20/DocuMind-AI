package com.documind;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:documind;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "documind.worker-enabled=false", "documind.daily-limit=30"
})
@AutoConfigureMockMvc
class QuizFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired QuizRepository repository;
    @Autowired GenerationRequestRepository budget;
    @Autowired QuizWorker worker;
    @MockitoBean QuizGenerator generator;
    final String token = UUID.randomUUID().toString();
    final String source = "A primary key uniquely identifies each row in a table. " + "A relational database stores information in tables. ".repeat(20);
    @BeforeEach void clear() { repository.deleteAll(); budget.deleteAll(); reset(generator); }
    List<Question> validQuestions() {
        return IntStream.range(0,10).mapToObj(i -> new Question("Practice question " + i + "?", List.of("Correct", "Other", "Third", "Fourth"),
            0, "A primary key identifies a row.", "A primary key uniquely identifies each row in a table.")).toList();
    }
    UUID upload() throws Exception {
        var response = mvc.perform(multipart("/api/quizzes").file(new MockMultipartFile("file", "notes.txt", "text/plain", source.getBytes(StandardCharsets.UTF_8)))
            .param("consent", "true").header("X-Session-Id", token)).andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("id").asText());
    }
    @Test void endToEndHidesAnswersAndGradesOnServer() throws Exception {
        when(generator.generate(anyString())).thenReturn(validQuestions());
        UUID id = upload();
        mvc.perform(get("/api/quizzes/"+id).header("X-Session-Id", token)).andExpect(jsonPath("$.quiz.status").value("QUEUED"));
        worker.process(repository.findById(id).orElseThrow());
        assertThat(repository.findById(id).orElseThrow().sourceText).isNull();
        mvc.perform(get("/api/quizzes/"+id).header("X-Session-Id", token))
            .andExpect(jsonPath("$.questions.length()").value(10)).andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
            .andExpect(jsonPath("$.questions[0].explanation").doesNotExist());
        mvc.perform(post("/api/quizzes/"+id+"/attempts").header("X-Session-Id", token).contentType("application/json")
            .content("{\"answers\":[0,0,0,0,0,1,1,1,1,1]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(5)).andExpect(jsonPath("$.total").value(10))
            .andExpect(jsonPath("$.review[0].sourceExcerpt").value("A primary key uniquely identifies each row in a table."));
    }
    @Test void sessionCannotReadDeleteOrGradeSomeoneElsesQuiz() throws Exception {
        UUID id = upload(); String other = UUID.randomUUID().toString();
        mvc.perform(get("/api/quizzes/"+id).header("X-Session-Id", other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/quizzes/"+id).header("X-Session-Id", other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/quizzes/"+id+"/attempts").header("X-Session-Id", other).contentType("application/json").content("{\"answers\":[0,0,0,0,0,0,0,0,0,0]}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/quizzes").header("X-Session-Id", other)).andExpect(content().json("[]"));
    }
    @Test void rejectsBadAnswersAndPendingAttempts() throws Exception {
        UUID id = upload();
        mvc.perform(post("/api/quizzes/"+id+"/attempts").header("X-Session-Id", token).contentType("application/json").content("{\"answers\":[0]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/quizzes/"+id+"/attempts").header("X-Session-Id", token).contentType("application/json").content("{\"answers\":[0,0,0,0,0,0,0,0,0,4]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/quizzes/"+id+"/attempts").header("X-Session-Id", token).contentType("application/json").content("{\"answers\":[0,0,0,0,0,0,0,0,0,0]}"))
            .andExpect(status().isConflict());
    }
    @Test void failedGenerationIsVisibleAndSourceIsCleared() throws Exception {
        when(generator.generate(anyString())).thenThrow(new IllegalStateException("AI quota reached."));
        UUID id = upload(); worker.process(repository.findById(id).orElseThrow());
        Quiz q = repository.findById(id).orElseThrow();
        assertThat(q.status).isEqualTo("FAILED"); assertThat(q.sourceText).isNull();
        mvc.perform(get("/api/quizzes/"+id).header("X-Session-Id", token)).andExpect(jsonPath("$.quiz.error").value("AI quota reached."));
    }
    @Test void invalidAIContentNeverBecomesReady() throws Exception {
        when(generator.generate(anyString())).thenReturn(List.of());
        UUID id = upload(); worker.process(repository.findById(id).orElseThrow());
        assertThat(repository.findById(id).orElseThrow().status).isEqualTo("FAILED");
    }
    @Test void queuedQuizCanBeDeleted() throws Exception {
        UUID id = upload();
        mvc.perform(delete("/api/quizzes/"+id).header("X-Session-Id", token)).andExpect(status().isNoContent());
        assertThat(repository.existsById(id)).isFalse();
    }
    @Test void enforcesQueueBound() throws Exception {
        for(int i=0;i<5;i++) upload();
        mvc.perform(multipart("/api/quizzes").file(new MockMultipartFile("file", "notes.txt", "text/plain", source.getBytes()))
            .param("consent", "true").header("X-Session-Id", token)).andExpect(status().isTooManyRequests());
    }
    @Test void rejectsMissingConsentInvalidSessionAndUnsupportedFiles() throws Exception {
        mvc.perform(multipart("/api/quizzes").file(new MockMultipartFile("file", "notes.exe", "application/octet-stream", source.getBytes()))
            .param("consent", "true").header("X-Session-Id", token)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/quizzes").file(new MockMultipartFile("file", "notes.txt", "text/plain", source.getBytes()))
            .param("consent", "false").header("X-Session-Id", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/quizzes").header("X-Session-Id", "bad-token")).andExpect(status().isBadRequest());
    }
    @Test void restartMarksInterruptedJobsFailedWithoutRetry() throws Exception {
        UUID id = upload(); Quiz q = repository.findById(id).orElseThrow(); q.status = "PROCESSING"; repository.saveAndFlush(q);
        worker.recover(); q = repository.findById(id).orElseThrow();
        assertThat(q.status).isEqualTo("FAILED"); assertThat(q.sourceText).isNull(); verifyNoInteractions(generator);
    }
    @Test void quotaCountsFailedJobsAndCannotBeResetByDeletion() throws Exception {
        UUID id = upload();
        mvc.perform(delete("/api/quizzes/"+id).header("X-Session-Id", token)).andExpect(status().isNoContent());
        assertThat(budget.count()).isEqualTo(1);
        for(int i=0;i<29;i++) budget.save(new GenerationRequest(java.time.Instant.now()));
        mvc.perform(multipart("/api/quizzes").file(new MockMultipartFile("file", "notes.txt", "text/plain", source.getBytes()))
            .param("consent", "true").header("X-Session-Id", token)).andExpect(status().isTooManyRequests());
    }
}
