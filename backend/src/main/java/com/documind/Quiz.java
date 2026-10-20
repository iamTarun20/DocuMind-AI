package com.documind;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quizzes")
class Quiz {
    @Id UUID id;
    @Column(nullable = false, length = 36) String ownerToken;
    @Column(nullable = false) String filename;
    @Column(nullable = false, length = 20) String status;
    @Column(columnDefinition = "TEXT") String sourceText;
    @Column(columnDefinition = "TEXT") String questionsJson;
    @Column(length = 500) String errorMessage;
    @Column(nullable = false) Instant createdAt;

    protected Quiz() {}
    Quiz(String ownerToken, String filename, String sourceText) {
        this.id = UUID.randomUUID();
        this.ownerToken = ownerToken;
        this.filename = filename;
        this.sourceText = sourceText;
        this.status = "QUEUED";
        this.createdAt = Instant.now();
    }
}
