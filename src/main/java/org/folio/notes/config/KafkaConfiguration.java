package org.folio.notes.config;

import java.util.UUID;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.event.DomainEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka producer configuration for Note domain events.
 *
 * <p>Defines the {@link ProducerFactory} and {@link KafkaTemplate} used to publish {@link DomainEvent} envelopes
 * keyed by the Note id. Serializers are driven by the {@code spring.kafka.producer} configuration
 * ({@code UUIDSerializer} for the key, {@code JacksonJsonSerializer} for the value).</p>
 */
@Configuration
public class KafkaConfiguration {

  @Bean
  public ProducerFactory<UUID, DomainEvent<Note>> noteEventProducerFactory(KafkaProperties kafkaProperties) {
    return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
  }

  @Bean
  public KafkaTemplate<UUID, DomainEvent<Note>> noteEventKafkaTemplate(
    ProducerFactory<UUID, DomainEvent<Note>> noteEventProducerFactory) {
    return new KafkaTemplate<>(noteEventProducerFactory);
  }
}
