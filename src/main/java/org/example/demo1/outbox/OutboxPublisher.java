package org.example.demo1.outbox;

import org.example.demo1.dao.OutboxEventDao;
import org.example.demo1.model.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventDao outboxEventDao;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventDao outboxEventDao, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventDao = outboxEventDao;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = outboxEventDao.findPendingEvents();
        if (pending.isEmpty()) return;

        logger.info("Found {} pending outbox events", pending.size());

        for (OutboxEventEntity ev : pending) {
            try {
                // send payload (string) to topic
                String topic = "inventory-events";
                CompletableFuture<SendResult<String, String>> future =
                        kafkaTemplate.send(topic, ev.getAggregateId(), ev.getPayload());

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to send outbox event id={}", ev.getId(), ex);
                        return;
                    }

                    // on success mark as SENT
                    try {
                        ev.setStatus("SENT");
                        ev.setSentAt(LocalDateTime.now());
                        outboxEventDao.save(ev);
                    } catch (Exception e) {
                        logger.error("Failed to mark outbox as sent id={}", ev.getId(), e);
                    }
                });

            } catch (Exception ex) {
                logger.error("Error publishing outbox event id={}", ev.getId(), ex);
            }
        }
    }
}


