package com.documind;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

interface QuizRepository extends JpaRepository<Quiz, UUID> {
    Optional<Quiz> findByIdAndOwnerToken(UUID id, String ownerToken);
    List<Quiz> findTop20ByOwnerTokenOrderByCreatedAtDesc(String ownerToken);
    Optional<Quiz> findFirstByStatusOrderByCreatedAtAsc(String status);
    List<Quiz> findByStatus(String status);
    @Transactional @Modifying
    @Query("update Quiz q set q.status = 'PROCESSING' where q.id = :id and q.status = 'QUEUED'")
    int claim(UUID id);
    @Transactional @Modifying
    @Query("delete from Quiz q where q.id = :id and q.ownerToken = :token and q.status <> 'PROCESSING'")
    int deleteIfNotProcessing(UUID id, String token);
    long countByStatusIn(Collection<String> statuses);
    void deleteByCreatedAtBefore(Instant before);
}
