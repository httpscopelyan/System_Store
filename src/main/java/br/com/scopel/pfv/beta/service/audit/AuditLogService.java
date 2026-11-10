package br.com.scopel.pfv.beta.service.audit;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.AuditLog;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    public static final String SYSTEM_ACTOR_NAME = "Sistema";
    public static final int MAX_PAGE_SIZE = 200;

    private static final int MAX_ACTOR_NAME_LENGTH = 50;
    private static final int MAX_TEXT_LENGTH = 255;

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(User actor, AuditAction action, AuditEntityType entityType, Long entityId,
                           String description, String previousValue, String newValue) {
        if (action == null || entityType == null) {
            throw new IllegalArgumentException("A ação e o tipo do registro de auditoria são obrigatórios.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A descrição do registro de auditoria é obrigatória.");
        }
        User persistedActor = actor != null && actor.getId() != null ? actor : null;
        return repository.save(new AuditLog(LocalDateTime.now(), persistedActor, actorNameOf(actor), action,
                entityType, entityId, clip(description.trim(), MAX_TEXT_LENGTH), clip(previousValue, MAX_TEXT_LENGTH),
                clip(newValue, MAX_TEXT_LENGTH)));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> recent(int pageNumber, int pageSize) {
        return repository.findAllByOrderByOccurredAtDescIdDesc(page(pageNumber, pageSize));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> byActor(Long actorId, int pageNumber, int pageSize) {
        requireActor(actorId);
        return repository.findByActor_IdOrderByOccurredAtDescIdDesc(actorId, page(pageNumber, pageSize));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> byPeriod(LocalDateTime start, LocalDateTime end, int pageNumber, int pageSize) {
        requirePeriod(start, end);
        return repository.findByOccurredAtBetweenOrderByOccurredAtDescIdDesc(start, end, page(pageNumber, pageSize));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> byActorAndPeriod(Long actorId, LocalDateTime start, LocalDateTime end, int pageNumber,
                                           int pageSize) {
        requireActor(actorId);
        requirePeriod(start, end);
        return repository.findByActor_IdAndOccurredAtBetweenOrderByOccurredAtDescIdDesc(actorId, start, end,
                page(pageNumber, pageSize));
    }

    private Pageable page(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("A página não pode ser negativa.");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("O tamanho da página deve ficar entre 1 e " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    private void requireActor(Long actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("Informe o usuário do histórico.");
        }
    }

    private void requirePeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Informe um período válido.");
        }
    }

    private String actorNameOf(User actor) {
        if (actor == null) {
            return SYSTEM_ACTOR_NAME;
        }
        String name = actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : actor.getLogin();
        return name == null || name.isBlank() ? SYSTEM_ACTOR_NAME : clip(name.trim(), MAX_ACTOR_NAME_LENGTH);
    }

    private String clip(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
