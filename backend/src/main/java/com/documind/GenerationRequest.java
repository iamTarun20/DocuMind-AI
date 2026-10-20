package com.documind;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "generation_requests")
class GenerationRequest {
    @Id UUID id;
    @Column(nullable = false) Instant createdAt;
    protected GenerationRequest() {}
    GenerationRequest(Instant at) { id = UUID.randomUUID(); createdAt = at; }
}
interface GenerationRequestRepository extends JpaRepository<GenerationRequest, UUID> {
    long countByCreatedAtAfter(Instant since);
    void deleteByCreatedAtBefore(Instant before);
}
