package org.folio.notes.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.event.DomainEvent;
import org.folio.notes.integration.kafka.NoteEventProducer;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

/**
 * Builds {@link DomainEvent} envelopes for Note changes and hands them to the {@link NoteEventProducer}.
 *
 * <p>Callers use the event-specific methods ({@link #publishNoteCreatedEvent(Note)},
 * {@link #publishNoteUpdatedEvent(Note, Note)}, {@link #publishNoteDeletedEvent(Note)}) and only need to provide the
 * relevant Note snapshot(s); this service takes care of resolving the current tenant via {@link FolioExecutionContext},
 * choosing the Kafka record key and populating the {@code old}/{@code new} envelope fields for each event type.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventPublisherService {

  private final NoteEventProducer noteEventProducer;
  private final FolioExecutionContext context;

  /**
   * Publishes a {@code CREATE} Note event carrying the new snapshot.
   *
   * @param newNote the newly-created Note snapshot
   */
  public void publishNoteCreatedEvent(Note newNote) {
    publish(DomainEvent.createEvent(newNote.getId(), newNote, context.getTenantId()));
  }

  /**
   * Publishes an {@code UPDATE} Note event carrying both the pre- and post-change snapshots.
   *
   * @param oldNote the pre-change Note snapshot
   * @param newNote the post-change Note snapshot
   */
  public void publishNoteUpdatedEvent(Note oldNote, Note newNote) {
    publish(DomainEvent.updateEvent(newNote.getId(), oldNote, newNote, context.getTenantId()));
  }

  /**
   * Publishes a {@code DELETE} Note event carrying the pre-delete snapshot.
   *
   * @param oldNote the pre-delete Note snapshot
   */
  public void publishNoteDeletedEvent(Note oldNote) {
    publish(DomainEvent.deleteEvent(oldNote.getId(), oldNote, context.getTenantId()));
  }

  private void publish(DomainEvent<Note> event) {
    log.debug("publish:: publishing Note event [id: {}, type: {}, tenant: {}]",
      event.getId(), event.getType(), event.getTenant());
    noteEventProducer.publish(event.getId(), event);
  }
}
