package com.documind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/quizzes")
class QuizController {
    private final QuizService service;
    QuizController(QuizService service) { this.service = service; }
    record Summary(UUID id, String filename, String status, String error, String createdAt) {}
    record PublicQuestion(int index, String prompt, List<String> options) {}
    record QuizView(Summary quiz, List<PublicQuestion> questions) {}
    record Answers(@NotNull @Size(min = 10, max = 10) List<@NotNull @Min(0) @Max(3) Integer> answers) {}
    record Review(String prompt, List<String> options, int selectedIndex, int correctIndex, boolean correct, String explanation, String sourceExcerpt) {}
    record Result(int score, int total, List<Review> review) {}
    private String owner(String token) {
        try { return UUID.fromString(token).toString(); }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "A valid browser session is required."); }
    }
    private Summary summary(Quiz q) { return new Summary(q.id, q.filename, q.status, q.errorMessage, q.createdAt.toString()); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Summary> create(@RequestHeader("X-Session-Id") String token, @RequestPart("file") MultipartFile file,
                                  @RequestParam boolean consent) {
        return ResponseEntity.accepted().body(summary(service.create(owner(token), file, consent)));
    }
    @GetMapping
    List<Summary> list(@RequestHeader("X-Session-Id") String token) {
        return service.list(owner(token)).stream().map(this::summary).toList();
    }
    @GetMapping("/{id}")
    QuizView get(@PathVariable UUID id, @RequestHeader("X-Session-Id") String token) {
        Quiz quiz = service.owned(id, owner(token));
        List<PublicQuestion> questions = new ArrayList<>();
        if ("READY".equals(quiz.status)) {
            var stored = service.questions(quiz);
            for (int i = 0; i < stored.size(); i++) questions.add(new PublicQuestion(i, stored.get(i).prompt(), stored.get(i).options()));
        }
        return new QuizView(summary(quiz), questions);
    }
    @PostMapping("/{id}/attempts")
    Result submit(@PathVariable UUID id, @RequestHeader("X-Session-Id") String token, @Valid @RequestBody Answers request) {
        var questions = service.questions(service.owned(id, owner(token)));
        List<Review> review = new ArrayList<>(); int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i); int answer = request.answers().get(i); boolean correct = answer == q.correctIndex();
            if (correct) score++;
            review.add(new Review(q.prompt(), q.options(), answer, q.correctIndex(), correct, q.explanation(), q.sourceExcerpt()));
        }
        return new Result(score, questions.size(), review);
    }
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @RequestHeader("X-Session-Id") String token) {
        service.delete(id, owner(token)); return ResponseEntity.noContent().build();
    }
}
