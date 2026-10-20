package com.documind;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class QuizService {
    private final QuizRepository repository;
    private final DocumentExtractor extractor;
    private final ObjectMapper mapper;
    private final int dailyLimit;
    private final GenerationRequestRepository budget;
    QuizService(QuizRepository repository, DocumentExtractor extractor, ObjectMapper mapper, GenerationRequestRepository budget,
                @Value("${documind.daily-limit}") int dailyLimit) {
        this.repository = repository; this.extractor = extractor; this.mapper = mapper; this.dailyLimit = dailyLimit; this.budget = budget;
    }
    // Serialized admission on the single-instance MVP prevents concurrent quota bypass.
    synchronized Quiz create(String token, MultipartFile file, boolean consent) {
        if (!consent) throw new ApiException(HttpStatus.BAD_REQUEST, "Consent to hosted AI processing is required.");
        if (budget.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(1))) >= dailyLimit)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The application's daily generation limit has been reached. Try tomorrow.");
        if (repository.countByStatusIn(List.of("QUEUED", "PROCESSING")) >= 5)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The generation queue is full. Please try later.");
        String text = extractor.extract(file);
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "document").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 255) name = name.substring(name.length() - 255);
        budget.saveAndFlush(new GenerationRequest(Instant.now()));
        return repository.saveAndFlush(new Quiz(token, name, text));
    }
    Quiz owned(UUID id, String token) {
        return repository.findByIdAndOwnerToken(id, token).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found in this browser session."));
    }
    List<Question> questions(Quiz quiz) {
        if (!"READY".equals(quiz.status)) throw new ApiException(HttpStatus.CONFLICT, "The quiz is not ready yet.");
        try { return mapper.readValue(quiz.questionsJson, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Stored quiz is invalid."); }
    }
    List<Quiz> list(String token) { return repository.findTop20ByOwnerTokenOrderByCreatedAtDesc(token); }
    void delete(UUID id, String token) {
        Quiz quiz = owned(id, token);
        if ("PROCESSING".equals(quiz.status)) throw new ApiException(HttpStatus.CONFLICT, "Wait for processing to finish before deleting this quiz.");
        if (repository.deleteIfNotProcessing(id, token) == 0) throw new ApiException(HttpStatus.CONFLICT, "This quiz is now processing. Wait before deleting it.");
    }
}
