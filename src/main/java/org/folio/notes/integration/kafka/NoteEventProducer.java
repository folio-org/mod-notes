package org.folio.notes.integration.kafka;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.event.DomainEvent;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes Note {@link DomainEvent}s to the tenant-scoped Kafka topic.
 *
 * <p>The topic name follows the FOLIO convention {@code {env}.{tenant}.notes.note} (for example
 * {@code folio.diku.notes.note}). The tenant is resolved at publish time via {@link FolioExecutionContext}
 * and {@code env} defaults to {@code folio}.</p>
 */
@Slf4j
@Component
public class NoteEventProducer {

  private final KafkaTemplate<UUID, DomainEvent<Note>> kafkaTemplate;
  private final FolioExecutionContext context;
  private final String env;
  private final String noteTopic;

  public NoteEventProducer(KafkaTemplate<UUID, DomainEvent<Note>> kafkaTemplate,
                           FolioExecutionContext context,
                           @Value("${folio.environment:folio}") String env,
                           @Value("${folio.kafka.topics.note:notes.note}") String noteTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.context = context;
    this.env = env;
    this.noteTopic = noteTopic;
  }

  /**
   * Publishes a Note domain event to the tenant-scoped topic.
   *
   * @param id    the Note id used as the Kafka record key
   * @param event the domain event envelope to publish
   */
  public void publish(UUID id, DomainEvent<Note> event) {
    var topic = topicName();
    try {
      log.debug("publish:: sending Note event [topic: {}, id: {}, type: {}]", topic, id, event.getType());
      kafkaTemplate.send(topic, id, event);
      log.info("publish:: Note event sent [topic: {}, id: {}, type: {}]", topic, id, event.getType());
    } catch (Exception e) {
      log.error("publish:: failed to send Note event [topic: {}, id: {}, type: {}]", topic, id, event.getType(), e);
    }
  }

  private String topicName() {
    return String.format("%s.%s.%s", env, context.getTenantId(), noteTopic);
  }
}
