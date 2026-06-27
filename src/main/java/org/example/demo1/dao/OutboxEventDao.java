package org.example.demo1.dao;

import jakarta.persistence.LockModeType;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DAO 层：负责访问 outbox 表。
 */
@Repository
public interface OutboxEventDao extends JpaRepository<OutboxEventEntity, Long> {

    // PESSIMISTIC_WRITE 会生成类似 SELECT ... FOR UPDATE 的锁，减少并发重复发布。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEventEntity o where o.status = 'PENDING' order by o.createdAt")
    List<OutboxEventEntity> findPendingEvents();
}
