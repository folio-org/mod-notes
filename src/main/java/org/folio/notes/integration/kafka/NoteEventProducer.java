package org.folio.notes.integration.kafka;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.event.DomainEvent;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.tools.kafka.FolioKafkaProperties;
import org.folio.spring.tools.kafka.KafkaUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes Note {@link DomainEvent}s to the tenant-scoped Kafka topic.
 *
 * <p>The topic name follows the FOLIO convention {@code {env}.{tenant}.notes.note} (for example
 * {@code folio.diku.notes.note}), resolved via {@link KafkaUtils#getTenantTopicName(String, String)} the same way the
 * topic is created on tenant enable. The tenant is resolved at publish time via {@link FolioExecutionContext}.</p>
 *
 * <p>Every message carries headers ({@code X-Okapi-Tenant}, {@code eventType}, {@code domain}) so consumers can
 * filter by tenant, event type or Note domain without deserializing the JSON payload.</p>
 */
@Slf4j
@Component
public class NoteEventProducer {

  private static final String EVENT_TYPE_HEADER = "eventType";
  private static final String EVENT_DOMAIN_HEADER = "domain";

  private final KafkaTemplate<UUID, DomainEvent<Note>> kafkaTemplate;
  private final FolioExecutionContext context;
  private final String noteTopic;

  public NoteEventProducer(KafkaTemplate<UUID, DomainEvent<Note>> kafkaTemplate,
                           FolioExecutionContext context,
                           FolioKafkaProperties kafkaProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.context = context;
    this.noteTopic = kafkaProperties.getTopics().getFirst().getName();
  }

  /**
   * Publishes a Note domain event to the tenant-scoped topic.
   *
   * @param id    the Note id used as the Kafka record key
   * @param event the domain event envelope to publish
   */
  public void publish(UUID id, DomainEvent<Note> event) {
    var topic = KafkaUtils.getTenantTopicName(noteTopic, context.getTenantId());
    try {
      log.debug("publish:: sending Note event [topic: {}, id: {}, type: {}]", topic, id, event.getType());
      var producerRecord = new ProducerRecord<>(topic, null, id, event, buildHeaders(event));
      kafkaTemplate.send(producerRecord);
      log.info("publish:: Note event sent [topic: {}, id: {}, type: {}]", topic, id, event.getType());
    } catch (Exception e) {
      log.error("publish:: failed to send Note event [topic: {}, id: {}, type: {}]", topic, id, event.getType(), e);
    }
  }

  private List<Header> buildHeaders(DomainEvent<Note> event) {
    var headers = new ArrayList<Header>();
    headers.add(header(EVENT_TYPE_HEADER, event.getType().name()));
    headers.add(header(EVENT_DOMAIN_HEADER, extractDomain(event)));
    context.getAllHeaders().forEach((key, value) -> headers.add(header(key, value.iterator().next())));
    return headers;
  }

  private Header header(String key, String value) {
    return new RecordHeader(key, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
  }

  private String extractDomain(DomainEvent<Note> event) {
    return Optional.ofNullable(event.getNewEntity())
      .or(() -> Optional.ofNullable(event.getOldEntity()))
      .map(Note::getDomain)
      .orElse(null);
  }
}
