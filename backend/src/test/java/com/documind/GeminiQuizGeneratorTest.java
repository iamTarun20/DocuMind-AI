package com.documind;

import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeminiQuizGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String source = "The Maratha administration maintained an o\ufb03ce for\u00a0civil administration.\n".repeat(40);

    List<GeminiQuizGenerator.GeneratedQuestion> drafts(int id) {
        return IntStream.range(0, 10).mapToObj(i -> new GeminiQuizGenerator.GeneratedQuestion(
            "What does concept " + i + " describe?", List.of("One", "Two", "Three", "Four"),
            0, "Explanation supported by the selected passage.", id)).toList();
    }

    @Test void passagesPreserveTheWholeDocumentAndBoundTheStoredExcerpts() {
        List<String> documents = List.of(source, "a".repeat(60000), "x".repeat(1200) + "tail", "a\n".repeat(18000));
        for (String document : documents) {
            var passages = GeminiQuizGenerator.sourcePassages(document);
            assertThat(passages.stream().map(GeminiQuizGenerator.SourcePassage::text).reduce("", String::concat)).isEqualTo(document);
            for (int i = 0; i < passages.size(); i++) {
                assertThat(passages.get(i).id()).isEqualTo(i + 1);
                assertThat(passages.get(i).text().length()).isBetween(15, 1500);
            }
        }
    }

    @Test void attachesOriginalPdfTextWithoutAskingTheModelToReproduceTypography() {
        var passages = GeminiQuizGenerator.sourcePassages(source);
        var questions = GeminiQuizGenerator.attachSourceExcerpts(drafts(1), passages);
        assertThat(questions).hasSize(10);
        assertThat(questions.get(0).sourceExcerpt()).isEqualTo(passages.get(0).text());
        assertThat(questions.get(0).sourceExcerpt()).contains("o\ufb03ce", "for\u00a0civil");
        assertThatCode(() -> GeminiQuizGenerator.validate(questions, source)).doesNotThrowAnyException();
    }

    @Test void rejectsMissingZeroAndOutOfRangePassageIds() {
        var passages = GeminiQuizGenerator.sourcePassages(source);
        for (Integer id : Arrays.asList(null, 0, -1, passages.size() + 1)) {
            var generated = new ArrayList<>(drafts(1));
            generated.set(6, new GeminiQuizGenerator.GeneratedQuestion("Question seven", List.of("One", "Two", "Three", "Four"),
                0, "Explanation", id));
            assertThatThrownBy(() -> GeminiQuizGenerator.attachSourceExcerpts(generated, passages))
                .hasMessageContaining("Question 7 failed validation")
                .hasMessageContaining("cited source passage does not exist");
        }
    }

    @Test void rejectsIncompleteGenerationAndInvalidQuestionsEvenWithAValidCitation() {
        var passages = GeminiQuizGenerator.sourcePassages(source);
        assertThatThrownBy(() -> GeminiQuizGenerator.attachSourceExcerpts(drafts(1).subList(0, 9), passages))
            .hasMessageContaining("10 valid questions");
        var generated = new ArrayList<>(drafts(1));
        generated.set(0, new GeminiQuizGenerator.GeneratedQuestion("Duplicate options?", List.of("Same", "Same", "Third", "Fourth"),
            0, "Explanation", 1));
        var questions = GeminiQuizGenerator.attachSourceExcerpts(generated, passages);
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(questions, source)).hasMessageContaining("answer options are duplicates");
    }

    @Test @SuppressWarnings("unchecked")
    void sendsNumberedPassagesAndResolvesStructuredResponseThroughTheRealGenerator() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
            "finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", mapper.writeValueAsString(drafts(1))))))))));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var generator = new GeminiQuizGenerator(mapper, "test-key-never-sent", "test-model", client);
        var questions = generator.generate(source);
        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(1)).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode body = mapper.readTree(requestBody(captured.getValue()));
        JsonNode document = mapper.readTree(body.path("contents").path(0).path("parts").path(0).path("text").asText());
        assertThat(document.path("sourcePassages").path(0).path("id").asInt()).isEqualTo(1);
        String firstPassage = document.path("sourcePassages").path(0).path("text").asText();
        assertThat(questions.get(0).sourceExcerpt()).isEqualTo(firstPassage);
        JsonNode properties = body.path("generationConfig").path("responseFormat").path("text").path("schema").path("items").path("properties");
        assertThat(properties.has("sourcePassageId")).isTrue();
        assertThat(properties.has("sourceExcerpt")).isFalse();
    }

    private String requestBody(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(2, TimeUnit.SECONDS);
    }
}
