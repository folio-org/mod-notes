package org.folio.notes.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.folio.notes.domain.dto.Note;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@UnitTest
class DomainEventTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void createEvent_setsCreateTypeAndOmitsOld() {
    var id = UUID.randomUUID();
    var note = new Note().title("t").domain("orders").typeId(UUID.randomUUID());

    var event = DomainEvent.createEvent(id, note, "diku");

    assertEquals(id, event.getId());
    assertEquals(DomainEventType.CREATE, event.getType());
    assertEquals("diku", event.getTenant());
    assertNull(event.getOldEntity());
    assertEquals(note, event.getNewEntity());
    assertNotNull(event.getTs());

    var json = MAPPER.writeValueAsString(event);
    assertTrue(json.contains("\"type\":\"CREATE\""));
    assertTrue(json.contains("\"new\""));
    assertFalse(json.contains("\"old\""), "NON_NULL must omit the null 'old' field");
  }

  @Test
  void updateEvent_carriesOldAndNew() {
    var id = UUID.randomUUID();
    var oldNote = new Note().title("old");
    var newNote = new Note().title("new");

    var event = DomainEvent.updateEvent(id, oldNote, newNote, "diku");

    assertEquals(DomainEventType.UPDATE, event.getType());
    assertEquals(oldNote, event.getOldEntity());
    assertEquals(newNote, event.getNewEntity());
  }

  @Test
  void deleteEvent_carriesOldAndOmitsNew() {
    var id = UUID.randomUUID();
    var oldNote = new Note().title("old");

    var event = DomainEvent.deleteEvent(id, oldNote, "diku");

    assertEquals(DomainEventType.DELETE, event.getType());
    assertEquals(oldNote, event.getOldEntity());
    assertNull(event.getNewEntity());
  }
}



