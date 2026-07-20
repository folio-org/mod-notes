package org.folio.notes.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope for a mod-notes domain event.
 *
 * @param <T> the payload type carried in the {@code old} and {@code new} fields (a full entity snapshot).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent<T> {

  private UUID id;
  @JsonProperty("old")
  private T oldEntity;
  @JsonProperty("new")
  private T newEntity;
  private DomainEventType type;
  private String tenant;
  private String ts;

  @JsonCreator
  public DomainEvent(@JsonProperty("id") UUID id,
                     @JsonProperty("old") T oldEntity,
                     @JsonProperty("new") T newEntity,
                     @JsonProperty("type") DomainEventType type,
                     @JsonProperty("tenant") String tenant,
                     @JsonProperty("ts") String ts) {
    this.id = id;
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
    this.type = type;
    this.tenant = tenant;
    this.ts = ts;
  }

  public static <T> DomainEvent<T> createEvent(UUID id, T newEntity, String tenant) {
    return new DomainEvent<>(id, null, newEntity, DomainEventType.CREATE, tenant, currentTs());
  }

  public static <T> DomainEvent<T> updateEvent(UUID id, T oldEntity, T newEntity, String tenant) {
    return new DomainEvent<>(id, oldEntity, newEntity, DomainEventType.UPDATE, tenant, currentTs());
  }

  public static <T> DomainEvent<T> deleteEvent(UUID id, T oldEntity, String tenant) {
    return new DomainEvent<>(id, oldEntity, null, DomainEventType.DELETE, tenant, currentTs());
  }

  private static String currentTs() {
    return String.valueOf(System.currentTimeMillis());
  }
}
