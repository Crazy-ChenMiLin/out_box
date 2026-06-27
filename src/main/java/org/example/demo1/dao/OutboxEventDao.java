package org.example.demo1.dao;

import jakarta.persistence.LockModeType;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventDao extends JpaRepository<OutboxEventEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEventEntity o where o.status = 'PENDING' order by o.createdAt")
    List<OutboxEventEntity> findPendingEvents();
}
