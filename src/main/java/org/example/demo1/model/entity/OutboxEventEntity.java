package org.example.demo1.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entity/PO：和 outbox 数据库表一一映射。
 *
 * outbox 表用于暂存“待发送到 Kafka 的事件”，状态从 PENDING 变成 SENT。
 */
@Entity
@Table(name = "outbox")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    // 聚合类型，比如 Inventory，表示这条事件属于哪类业务对象。
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    // 聚合 ID，这里保存 inventory 表的主键 ID。
    private String aggregateId;

    @Column(nullable = false)
    // 事件类型，比如 INVENTORY_DECREMENTED。
    private String type;

    @Lob
    @Column(nullable = false)
    // 发送给 Kafka 的 JSON 字符串内容。
    private String payload;

    @Column(nullable = false, length = 32)
    // 事件状态：PENDING 表示待发送，SENT 表示已发送成功。
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public OutboxEventEntity() {
    }

    public OutboxEventEntity(String aggregateType, String aggregateId, String type, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
