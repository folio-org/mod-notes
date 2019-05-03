package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import static org.folio.util.NoteTestData.NOTE_2;
import static org.folio.util.NoteTestData.PACKAGE_ID;
import static org.folio.util.NoteTestData.PACKAGE_TYPE;
import static org.folio.util.NoteTestData.USER8;
import static org.folio.util.TestUtil.readFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.persist.PostgresClient;

@RunWith(VertxUnitRunner.class)
public class NoteLinksImplTest extends TestBase {

  private static final int DEFAULT_LINK_INDEX = 0;
  private static final int DEFAULT_LINK_AMOUNT = 1;

  @BeforeClass
  public static void setUpBeforeClass(TestContext context) {
    createNoteTypes(context);
  }

  @Before
  public void setUp() throws Exception {
    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/88888888-8888-4888-8888-888888888888"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_another_user.json"))
        ));
  }

  @After
  public void tearDown() {
    DBTestUtil.deleteFromTable(vertx,
      (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TABLE));
  }

  @Test
  public void shouldAddLinksToMultipleNotes() {
    Note firstNote = createNote();
    Note secondNote = createNote();
    Note thirdNote = createNote();
    createLinks(firstNote.getId(), thirdNote.getId());

    List<Note> notes = getNotes();

    Note firstResultNote = getNoteById(notes, firstNote.getId());
    Note secondResultNote = getNoteById(notes, secondNote.getId());
    Note thirdResultNote = getNoteById(notes, thirdNote.getId());

    assertEquals(PACKAGE_TYPE, firstResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getType());
    assertEquals(PACKAGE_ID, firstResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getId());

    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());

    assertEquals(PACKAGE_TYPE, thirdResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getType());
    assertEquals(PACKAGE_ID, thirdResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getId());
  }

  @Test
  public void shouldRemoveLinksFromMultipleNotes() {
    Note firstNote = createNote();
    Note secondNote = createNote();
    Note thirdNote = createNote();
    createLinks(firstNote.getId(), secondNote.getId(), thirdNote.getId());

    removeLinks(secondNote.getId(), thirdNote.getId());

    List<Note> notes = getNotes();

    Note firstResultNote = getNoteById(notes, firstNote.getId());
    Note secondResultNote = getNoteById(notes, secondNote.getId());
    Note thirdResultNote = getNoteById(notes, thirdNote.getId());
    assertEquals(DEFAULT_LINK_AMOUNT + 1, firstResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, thirdResultNote.getLinks().size());
  }

  @Test
  public void shouldRemoveAndAddLinksToNotes() {
    Note firstNote = createNote();
    Note secondNote = createNote();
    Note thirdNote = createNote();
    createLinks(firstNote.getId(), secondNote.getId());

    NoteLinksPut putRequest = new NoteLinksPut()
      .withNotes(
        Arrays.asList(
          createNoteLink(firstNote.getId(), NoteLinkPut.Status.UNASSIGNED),
          createNoteLink(secondNote.getId(), NoteLinkPut.Status.UNASSIGNED),
          createNoteLink(thirdNote.getId(), NoteLinkPut.Status.ASSIGNED))
      );
    putLinks(putRequest);

    List<Note> notes = getNotes();

    Note firstResultNote = getNoteById(notes, firstNote.getId());
    Note secondResultNote = getNoteById(notes, secondNote.getId());
    Note thirdResultNote = getNoteById(notes, thirdNote.getId());
    assertEquals(DEFAULT_LINK_AMOUNT, firstResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());

    assertEquals(PACKAGE_TYPE, thirdResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getType());
    assertEquals(PACKAGE_ID, thirdResultNote.getLinks().get(DEFAULT_LINK_INDEX + 1).getId());
  }

  @Test
  public void shouldNotAddLinkForTheSecondTime() {
    Note note = createNote();
    createLinks(note.getId());
    createLinks(note.getId());

    List<Note> notes = getNotes();

    assertEquals(DEFAULT_LINK_AMOUNT + 1, getNoteById(notes, note.getId()).getLinks().size());
  }

  @Test
  public void shouldIgnoreSecondRemoveRequest() {
    Note note = createNote();
    createLinks(note.getId());
    removeLinks(note.getId());
    removeLinks(note.getId());
    List<Note> notes = getNotes();
    assertEquals(DEFAULT_LINK_AMOUNT, getNoteById(notes, note.getId()).getLinks().size());
  }

  @Test
  public void shouldRemoveNoteWhenLastLinkIsRemoved() {
    Note note = getNote()
      .withLinks(Collections.singletonList(new Link()
        .withId(PACKAGE_ID)
        .withType(PACKAGE_TYPE)
    ));
    postNoteWithOk(Json.encode(note), USER8);
    removeLinks(note.getId());
    List<Note> notes = getNotes();
    assertFalse(notes.stream().anyMatch(resultNote-> note.getId().equals(resultNote.getId())));
  }

  private void putLinks(NoteLinksPut requestBody) {
    putWithOk(
      "note-links/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID,
      Json.encode(requestBody), USER8);
  }

  private void createLinks(String... ids) {
    changeLinks(ids, NoteLinkPut.Status.ASSIGNED);
  }

  private void removeLinks(String... ids) {
    changeLinks(ids, NoteLinkPut.Status.UNASSIGNED);
  }

  private void changeLinks(String[] ids, NoteLinkPut.Status assigned) {
    NoteLinksPut putRequest = new NoteLinksPut()
      .withNotes(
        Arrays.stream(ids)
          .map(id -> createNoteLink(id, assigned))
          .collect(Collectors.toList())
      );

    putLinks(putRequest);
  }

  private Note getNoteById(List<Note> notes, String id) {
    return notes.stream()
      .filter(note -> note.getId().equals(id))
      .findFirst().get();
  }

  private NoteLinkPut createNoteLink(String id, NoteLinkPut.Status status) {
    return new NoteLinkPut()
      .withId(id)
      .withStatus(status);
  }

  private Note createNote() {
    Note note = getNote();
    postNoteWithOk(Json.encode(note), USER8);
    return note;
  }

  private Note getNote() {
    return Json.decodeValue(NOTE_2, Note.class)
      .withId(UUID.randomUUID().toString());
  }

  private List<Note> getNotes() {
    return getWithOk("/notes").as(NoteCollection.class).getNotes();
  }
}
