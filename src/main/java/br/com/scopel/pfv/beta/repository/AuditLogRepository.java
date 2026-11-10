package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<AuditLog> findByActor_IdOrderByOccurredAtDescIdDesc(Long actorId, Pageable pageable);

    Page<AuditLog> findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(LocalDateTime start, LocalDateTime end,
                                                                      Pageable pageable);

    Page<AuditLog> findByActor_IdAndOccurredAtBetweenOrderByOccurredAtDescIdDesc(Long actorId, LocalDateTime start,
                                                                                 LocalDateTime end,
                                                                                 Pageable pageable);
}
