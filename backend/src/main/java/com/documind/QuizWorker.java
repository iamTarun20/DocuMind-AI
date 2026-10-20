package com.documind;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class QuizWorker {
    private final QuizRepository repository;
    private final QuizGenerator generator;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final GenerationRequestRepository budget;
    QuizWorker(QuizRepository repository, QuizGenerator generator, ObjectMapper mapper, GenerationRequestRepository budget,
               @Value("${documind.worker-enabled}") boolean enabled) {
        this.repository = repository; this.generator = generator; this.mapper = mapper; this.enabled = enabled; this.budget = budget;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        // One backend replica only: interrupted jobs fail explicitly; no silent paid retries.
        for (Quiz q : repository.findByStatus("PROCESSING")) {
            q.status = "FAILED"; q.errorMessage = "Processing was interrupted by a restart. Please upload again.";
            q.sourceText = null; repository.save(q);
        }
    }
    @Scheduled(fixedDelay = 1500)
    public void tick() {
        if (!enabled) return;
        repository.findFirstByStatusOrderByCreatedAtAsc("QUEUED").ifPresent(this::process);
    }
    void process(Quiz quiz) {
        if (repository.claim(quiz.id) == 0) return;
        quiz.status = "PROCESSING";
        try {
            var questions = generator.generate(quiz.sourceText);
            GeminiQuizGenerator.validate(questions, quiz.sourceText);
            quiz.questionsJson = mapper.writeValueAsString(questions);
            quiz.status = "READY";
        } catch (IllegalStateException e) {
            quiz.status = "FAILED"; quiz.errorMessage = e.getMessage();
        } catch (Exception e) {
            quiz.status = "FAILED"; quiz.errorMessage = "Generation could not be completed. Please upload again.";
        } finally {
            quiz.sourceText = null;
            // Deletion of processing jobs is blocked. Avoid resurrecting externally deleted jobs.
            if (repository.existsById(quiz.id)) repository.saveAndFlush(quiz);
        }
    }
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanup() {
        repository.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(7)));
        budget.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
