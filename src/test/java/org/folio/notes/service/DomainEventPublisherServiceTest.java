package org.folio.notes.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.event.DomainEvent;
import org.folio.notes.domain.event.DomainEventType;
import org.folio.notes.integration.kafka.NoteEventProducer;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherServiceTest {

  @Mock
  private NoteEventProducer noteEventProducer;
  @Mock
  private FolioExecutionContext context;
  @InjectMocks
  private DomainEventPublisherService service;

  @Test
  void publishNoteCreatedEvent_buildsCreateEnvelopeWithoutOld() {
    var id = UUID.randomUUID();
    var note = new Note().id(id).title("t");
    when(context.getTenantId()).thenReturn("diku");

    service.publishNoteCreatedEvent(note);

    var captor = eventCaptor();
    verify(noteEventProducer).publish(eq(id), captor.capture());
    var event = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(DomainEventType.CREATE, event.getType());
    org.junit.jupiter.api.Assertions.assertEquals("diku", event.getTenant());
    org.junit.jupiter.api.Assertions.assertEquals(note, event.getNewEntity());
    org.junit.jupiter.api.Assertions.assertNull(event.getOldEntity());
  }

  @Test
  void publishNoteUpdatedEvent_carriesOldAndNew() {
    var id = UUID.randomUUID();
    var oldNote = new Note().id(id).title("old");
    var newNote = new Note().id(id).title("new");
    when(context.getTenantId()).thenReturn("diku");

    service.publishNoteUpdatedEvent(oldNote, newNote);

    var captor = eventCaptor();
    verify(noteEventProducer).publish(eq(id), captor.capture());
    var event = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(DomainEventType.UPDATE, event.getType());
    org.junit.jupiter.api.Assertions.assertEquals(oldNote, event.getOldEntity());
    org.junit.jupiter.api.Assertions.assertEquals(newNote, event.getNewEntity());
  }

  @Test
  void publishNoteDeletedEvent_carriesOldAndOmitsNew() {
    var id = UUID.randomUUID();
    var oldNote = new Note().id(id).title("old");
    when(context.getTenantId()).thenReturn("diku");

    service.publishNoteDeletedEvent(oldNote);

    var captor = eventCaptor();
    verify(noteEventProducer).publish(eq(id), captor.capture());
    var event = captor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(DomainEventType.DELETE, event.getType());
    org.junit.jupiter.api.Assertions.assertEquals(oldNote, event.getOldEntity());
    org.junit.jupiter.api.Assertions.assertNull(event.getNewEntity());
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<DomainEvent<Note>> eventCaptor() {
    return ArgumentCaptor.forClass(DomainEvent.class);
  }
}

