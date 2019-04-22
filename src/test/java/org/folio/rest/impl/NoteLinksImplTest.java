package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.folio.util.NoteTestData.*;
import static org.folio.util.TestUtil.readFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
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

    assertEquals(DOMAIN, firstResultNote.getLinks().get(0).getDomain());
    assertEquals(PACKAGE_TYPE, firstResultNote.getLinks().get(0).getType());
    assertEquals(PACKAGE_ID, firstResultNote.getLinks().get(0).getId());

    assertThat(secondResultNote.getLinks(), is(empty()));

    assertEquals(DOMAIN, thirdResultNote.getLinks().get(0).getDomain());
    assertEquals(PACKAGE_TYPE, thirdResultNote.getLinks().get(0).getType());
    assertEquals(PACKAGE_ID, thirdResultNote.getLinks().get(0).getId());
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
    assertThat(firstResultNote.getLinks(), is(not(empty())));
    assertThat(secondResultNote.getLinks(), is(empty()));
    assertThat(thirdResultNote.getLinks(), is(empty()));
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
    assertThat(firstResultNote.getLinks(), is(empty()));
    assertThat(secondResultNote.getLinks(), is(empty()));

    assertEquals(DOMAIN, thirdResultNote.getLinks().get(0).getDomain());
    assertEquals(PACKAGE_TYPE, thirdResultNote.getLinks().get(0).getType());
    assertEquals(PACKAGE_ID, thirdResultNote.getLinks().get(0).getId());
  }

  @Test
  public void shouldNotAddLinkForTheSecondTime() {
    Note note = createNote();
    createLinks(note.getId());
    createLinks(note.getId());

    List<Note> notes = getNotes();

    assertEquals(1, getNoteById(notes, note.getId()).getLinks().size());
  }

  @Test
  public void shouldIgnoreSecondRemoveRequest() {
    Note note = createNote();
    createLinks(note.getId());
    removeLinks(note.getId());
    removeLinks(note.getId());
    List<Note> notes = getNotes();
    assertEquals(0, getNoteById(notes, note.getId()).getLinks().size());
  }

  private void putLinks(NoteLinksPut requestBody) {
    putWithStatus(
      "note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID,
      Json.encode(requestBody),
      HttpStatus.SC_NO_CONTENT);
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

  private Note createNote() {
    Note note = getNote();
    sendNotePostRequest(Json.encode(note), USER8);
    return note;
  }

  private Note getNote() {
    return Json.decodeValue(NOTE_2, Note.class)
      .withId(UUID.randomUUID().toString());
  }

  private NoteLinkPut createNoteLink(String id, NoteLinkPut.Status status) {
    return new NoteLinkPut()
      .withId(id)
      .withStatus(status);
  }

  private List<Note> getNotes() {
    return getWithOk("/notes").as(NoteCollection.class).getNotes();
  }
}
