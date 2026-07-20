package org.folio.notes.controller;

import static org.folio.notes.support.DatabaseHelper.LINK;
import static org.folio.notes.support.DatabaseHelper.NOTE;
import static org.folio.notes.support.DatabaseHelper.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.folio.notes.domain.dto.Link;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.User;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.support.TestApiBase;
import org.folio.notes.support.TestKafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import tools.jackson.databind.JsonNode;

@DisplayName("Note create domain events")
class NoteDomainEventIT extends TestApiBase {

  private static final String NOTE_URL = "/notes";
  private static final String NOTE_TOPIC = "folio.test.notes.note";
  private static final String ORDER_LINE_TYPE = "order-line";

  private static final UUID NOTE_TYPE_ID = UUID.fromString("2af21797-d25b-46dc-8427-1759d1db2057");
  private static final String NOTE_TYPE_NAME = "General note";

  @Autowired
  private KafkaProperties kafkaProperties;

  private TestKafkaConsumer consumer;

  @BeforeEach
  void setUp() {
    stubUser(new User(USER_ID, "test_user", null));
    databaseHelper.clearTable(TENANT, NOTE);
    databaseHelper.clearTable(TENANT, LINK);
    databaseHelper.clearTable(TENANT, TYPE);

    var noteType = new NoteTypeEntity();
    noteType.setId(NOTE_TYPE_ID);
    noteType.setName(NOTE_TYPE_NAME);
    noteType.setCreatedBy(USER_ID);
    databaseHelper.saveNoteType(noteType, TENANT);

    consumer = TestKafkaConsumer.subscribe(NOTE_TOPIC, kafkaProperties);
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.close();
    }
  }

  @Test
  @DisplayName("create publishes a CREATE event with the full snapshot and no 'old' field")
  void createNote_publishesCreateEvent() throws Exception {
    var link = new Link().id(UUID.randomUUID().toString()).type(ORDER_LINE_TYPE);
    var note = new Note()
      .title("Kafka title")
      .content("Kafka details")
      .domain("orders")
      .typeId(NOTE_TYPE_ID)
      .links(List.of(link));

    var response = mockMvc.perform(post(NOTE_URL).headers(okapiHeaders()).content(asJsonString(note)))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();

    var event = consumer.poll();
    var envelope = OBJECT_MAPPER.readTree(event.value());

    assertEnvelope(envelope, event.value());
    assertPayload(envelope.get("new"), OBJECT_MAPPER.readTree(response).get("id").asString(), link);
  }

  @Test
  @DisplayName("invalid payload (missing title) publishes no event")
  void createNote_invalidPayload_publishesNoEvent() throws Exception {
    var note = new Note()
      .domain("orders")
      .typeId(NOTE_TYPE_ID)
      .links(List.of(new Link().id(UUID.randomUUID().toString()).type(ORDER_LINE_TYPE)));

    mockMvc.perform(post(NOTE_URL).headers(okapiHeaders()).content(asJsonString(note)))
      .andExpect(status().is4xxClientError());

    var event = consumer.pollNullable(Duration.ofSeconds(10));
    assertNull(event, "No event should be published for an invalid create");
  }

  private void assertEnvelope(JsonNode envelope, String rawJson) {
    assertNotNull(envelope.get("id"), "eventId (id) must be present");
    assertEquals("CREATE", envelope.get("type").asString());
    assertEquals(TENANT, envelope.get("tenant").asString());
    assertNull(envelope.get("old"), "CREATE event must not carry an 'old' field");
    assertFalse(rawJson.contains("\"old\""), "JSON must not contain an 'old' field");
  }

  private void assertPayload(JsonNode newNode, String createdId, Link link) {
    assertNotNull(newNode);
    assertEquals(createdId, newNode.get("id").asString());
    assertEquals("Kafka title", newNode.get("title").asString());
    assertEquals("Kafka details", newNode.get("content").asString());
    assertEquals(NOTE_TYPE_ID.toString(), newNode.get("typeId").asString());
    assertEquals(USER_ID.toString(), newNode.get("metadata").get("createdByUserId").asString());

    var links = newNode.get("links");
    assertEquals(1, links.size());
    assertEquals(ORDER_LINE_TYPE, links.get(0).get("type").asString());
    assertEquals(link.getId(), links.get(0).get("id").asString());
  }
}



