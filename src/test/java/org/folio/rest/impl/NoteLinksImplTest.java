package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.folio.test.util.TestUtil.readFile;
import static org.folio.util.NoteTestData.NOTE_2;
import static org.folio.util.NoteTestData.PACKAGE_ID;
import static org.folio.util.NoteTestData.PACKAGE_TYPE;
import static org.folio.util.NoteTestData.USER8;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class NoteLinksImplTest extends TestBase {

  private static final int DEFAULT_LINK_INDEX = 0;
  private static final int DEFAULT_LINK_AMOUNT = 1;
  private static final String INVALID_ID = "invalid id";
  private static final String NOTE_LINKS_PATH = "note-links/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID;
  private static final String NON_EXISTING_ID = "11111111111111";
  private static final String DOMAIN = "eholdings";
  private static final String NON_EXISTING_DOMAIN = "nonExisting";

  @BeforeClass
  public static void setUpBeforeClass(TestContext context) {
    TestBase.setUpBeforeClass(context);
    createNoteTypes(context);
  }

  @AfterClass
  public static void tearDownAfterClass() {
    DBTestUtil.deleteAllNoteTypes(vertx);
    TestBase.tearDownAfterClass();
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
    DBTestUtil.deleteAllNotes(vertx);
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
    assertFalse(notes.stream().anyMatch(resultNote -> note.getId().equals(resultNote.getId())));
  }

  @Test
  public void shouldDoNothingOnEmptyList() {
    removeLinks();
    List<Note> notes = getNotes();
    assertTrue(notes.isEmpty());
  }

  @Test
  public void shouldReturn500OnTransactionFailure() {
    String putBody = Json.encode(createPutLinksRequest(NoteLinkPut.Status.ASSIGNED, INVALID_ID));
    putWithStatus(NOTE_LINKS_PATH, putBody, 500, USER8);
  }

  @Test
  public void shouldReturnListOfNotesWithoutParameters() {
    createNote();
    createNote();

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  public void shouldReturnEmptyNoteCollection() {

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(0, notes.size());
  }

  @Test
  public void shouldReturnListOfNotesWithLimit() {
    createNote();
    createNote();

    NoteCollection notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789?limit=1")
      .as(NoteCollection.class);

    assertEquals(1, notes.getNotes().size());
    assertEquals(2, (int)notes.getTotalRecords());
  }

  @Test
  public void shouldReturnListOfNotesWithOffset() {
    createNote();
    createNote();

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789?offset=1")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  public void shouldReturnAllRecordsOfNotesFromDBWithoutParametersAndWithNonExistingId() {
    createNote();
    createNote();

    List<Note> notes = getWithOk(
      "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + NON_EXISTING_ID)
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  public void shouldReturnAllRecordsOfNotesFromDBWithoutParametersAndWithIncompleteUrl() {
    createNote();
    createNote();

    List<Note> notes = getWithOk(
      "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID + "?orde")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  public void shouldReturnListOfNotesWithAssignedStatus() {
    Note firstNote = createNote();
    createNote();
    createLinks(firstNote.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?status=ASSIGNED")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  public void shouldReturnListOfNotesSortedByTitleAsc() {
    Note firstNote = getNote().withTitle("ABC");
    Note secondNote = getNote().withTitle("ZZZ");
    postNoteWithOk(Json.encode(firstNote), USER8);
    postNoteWithOk(Json.encode(secondNote), USER8);
    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?order=asc&orderBy=title")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getTitle(), notes.get(0).getTitle());
    assertEquals(secondNote.getTitle(), notes.get(1).getTitle());
  }

  @Test
  public void shouldReturnListOfNotesSortedByTitleDesc() {
    Note firstNote = getNote().withTitle("ABC");
    Note secondNote = getNote().withTitle("ZZZ");
    postNoteWithOk(Json.encode(firstNote), USER8);
    postNoteWithOk(Json.encode(secondNote), USER8);
    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?order=desc&orderBy=title")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
    assertEquals(secondNote.getTitle(), notes.get(0).getTitle());
    assertEquals(firstNote.getTitle(), notes.get(1).getTitle());
  }

  @Test
  public void shouldReturnListOfNotesSearchedByTitle() {
    Note firstNote = getNote().withTitle("Title ABC");
    Note secondNote = getNote().withTitle("Title ZZZ ABC");
    postNoteWithOk(Json.encode(firstNote), USER8);
    postNoteWithOk(Json.encode(secondNote), USER8);
    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?title=ZZZ")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(1, notes.size());
    assertEquals(secondNote.getTitle(), notes.get(0).getTitle());
  }

  @Test
  public void shouldReturnListOfNotesWithUnassignedStatus() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?status=UNASSIGNED")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  public void shouldReturnListOfNotesWithNonExistingDomain() {
    Note firsNoteWithAssignedLink = createNote();
    Note secondNoteWithUnassignedLink = createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    List<Note> notes = getWithOk(
      "/note-links/domain/" + NON_EXISTING_DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
        + "?status=UNASSIGNED")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(0, notes.size());
    assertEquals(DEFAULT_LINK_AMOUNT, firsNoteWithAssignedLink.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, secondNoteWithUnassignedLink.getLinks().size());
  }

  @Test
  public void shouldReturnListOfNotesWithStatusAll() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    List<Note> notes = getWithOk(
      "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID + "?status=all")
      .as(NoteCollection.class)
      .getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  public void shouldReturnListOfNotesWithOrderDescByStatus() {
    Note firsNoteWithAssignedLink = createNote();
    Note secondNoteWithUnassignedLink = createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    List<Note> notes = getWithOk("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID
      + "?order=desc&orderBy=status")
      .as(NoteCollection.class)
      .getNotes();

    Note firstResultNote = getNoteById(notes, firsNoteWithAssignedLink.getId());

    assertEquals(2, notes.size());
    assertEquals(secondNoteWithUnassignedLink.getId(), notes.get(0).getId());
    assertEquals(2, notes.get(1).getLinks().size());

    assertEquals(PACKAGE_ID, firstResultNote.getLinks().get(1).getId());
    assertEquals(PACKAGE_TYPE, firstResultNote.getLinks().get(1).getType());
  }

  @Test
  public void shouldReturnListOfNotesWithOrderInUpper() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    List<Note> notes = getWithOk(
      "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID + "?order=ASC&orderBy=status")
      .as(NoteCollection.class)
      .getNotes();

    Note firstResultNote = getNoteById(notes, firsNoteWithAssignedLink.getId());

    assertEquals(2, notes.size());
    assertEquals(firsNoteWithAssignedLink.getId(), notes.get(0).getId());
    assertEquals(2, notes.get(0).getLinks().size());

    assertEquals(PACKAGE_ID, firstResultNote.getLinks().get(1).getId());
    assertEquals(PACKAGE_TYPE, firstResultNote.getLinks().get(1).getType());
  }

  @Test
  public void shouldReturn400WithErrorMessageWrongOrder() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    final String response = getWithStatus("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/" + PACKAGE_ID + "?order=wrong&orderBy=status", 400)
      .asString();

    assertThat(response, containsString("Order is incorrect"));
  }

  @Test
  public void shouldReturn400WithErrorMessageWrongStatus() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    final String response = getWithStatus("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/" + PACKAGE_ID + "?status=wrong", 400)
      .asString();

    assertThat(response, containsString("Status is incorrect"));
  }

  @Test
  public void shouldReturn400WithErrorMessageWrongLimitAndOffset() {
    Note firsNoteWithAssignedLink = createNote();
    createNote();
    createLinks(firsNoteWithAssignedLink.getId());

    final String response = getWithStatus("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/" + PACKAGE_ID + "?limit=-1&offset=-1", 400)
      .asString();

    assertThat(response, containsString("parameter is incorrect"));
  }

  private void putLinks(NoteLinksPut requestBody) {
    putWithOk(NOTE_LINKS_PATH, Json.encode(requestBody), USER8);
  }

  private void createLinks(String... ids) {
    changeLinks(ids, NoteLinkPut.Status.ASSIGNED);
  }

  private void removeLinks(String... ids) {
    changeLinks(ids, NoteLinkPut.Status.UNASSIGNED);
  }

  private void changeLinks(String[] ids, NoteLinkPut.Status assigned) {
    NoteLinksPut putRequest = createPutLinksRequest(assigned, ids);
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

  private NoteLinksPut createPutLinksRequest(NoteLinkPut.Status assigned, String... ids) {
    return new NoteLinksPut()
      .withNotes(
        Arrays.stream(ids)
          .map(id -> createNoteLink(id, assigned))
          .collect(Collectors.toList())
      );
  }

  private List<Note> getNotes() {
    return getWithOk("/notes").as(NoteCollection.class).getNotes();
  }
}
