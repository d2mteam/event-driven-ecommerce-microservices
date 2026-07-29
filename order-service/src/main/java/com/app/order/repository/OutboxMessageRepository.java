package com.app.order.repository;

import com.app.order.entity.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxMessageRepository
        extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
