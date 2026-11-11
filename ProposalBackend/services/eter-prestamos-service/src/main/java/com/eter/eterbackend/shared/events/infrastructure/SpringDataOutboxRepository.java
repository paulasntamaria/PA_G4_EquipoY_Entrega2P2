package com.eter.eterbackend.shared.events.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

	boolean existsByDedupKey(String dedupKey);

	List<OutboxEventJpaEntity> findByPublishedAtIsNullOrderByOccurredAtAsc(Limit limite);
}
