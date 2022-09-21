package org.folio.notes.controller;

import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.folio.notes.support.DatabaseHelper.LINK;
import static org.folio.notes.support.DatabaseHelper.NOTE;
import static org.folio.notes.support.DatabaseHelper.TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolationException;
import org.apache.http.HttpStatus;
import org.folio.notes.domain.dto.Link;
import org.folio.notes.domain.dto.LinkStatus;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.dto.NoteLinkUpdate;
import org.folio.notes.domain.dto.NoteLinkUpdateCollection;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.User;
import org.folio.notes.domain.entity.NoteEntity;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.exception.NoteNotFoundException;
import org.folio.notes.support.TestApiBase;
import org.folio.spring.cql.CqlQueryValidationException;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class NotesControllerTest extends TestApiBase {

  private static final String NOTE_URL = "/notes";
  private static final String NOTE_TYPE_URL = "/note-types";
  private static final String LINK_ID = "123-456789";
  private static final String NOTE_TYPE_ID_1 = "13f21797-d25b-46dc-8427-1759d1db2057";
  private static final String NOTE_TYPE_ID_2 = "2af21797-d25b-46dc-8427-1759d1db2057";
  private static final String PACKAGE_ID_1 = "18-2356521";
  private static final String PACKAGE_ID_2 = "123-456789";
  private static final String NOTE_TYPE_NAME_1 = "High Priority";
  private static final String NOTE_TYPE_NAME_2 = "test note";
  private static final String PACKAGE_TYPE = "package";
  private static final String DOMAIN = "domain";
  private static final String NON_EXISTING_ID = "11111111111111";
  private static final String NON_EXISTING_DOMAIN = "nonExistingDomain";
  private static final String NOTE_LINKS_PATH = "/note-links/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1;

  private static final UUID[] NOTE_IDS = new UUID[] {randomUUID(), randomUUID(), randomUUID()};
  private static final String[] NOTE_TITLES = new String[] {"First", "Second", "Third"};
  private static final String[] NOTE_DOMAINS = new String[] {"users", "titles", "titles"};
  private static final String[] NOTE_CONTENT = new String[] {"first content", "second", "third content"};
  private static final String[] TYPE_NAMES = new String[] {"General Note", "Special"};
  private static final UUID[] TYPE_IDS = new UUID[] {randomUUID(), randomUUID()};
  private static final int DEFAULT_LINK_AMOUNT = 1;

  @Value("${folio.notes.types.defaults.limit}")
  private String defaultNoteTypeLimit;

  private static Stream<Arguments> cqlQueryProvider() {
    return Stream.of(
      arguments("id = \"" + NOTE_IDS[0] + "\""),
      arguments("title = \"" + NOTE_TITLES[0] + "\""),
      arguments("domain == \"" + NOTE_DOMAINS[0] + "\""),
      arguments("content all \"" + NOTE_CONTENT[0] + "\""),
      arguments("type.id = \"" + TYPE_IDS[0] + "\""),
      arguments("type.name = \"" + TYPE_NAMES[0] + "\""),
      arguments("links.objectId = \"" + PACKAGE_ID_1 + "\""),
      arguments("links.objectType = \"" + PACKAGE_TYPE + "\""),
      arguments("createdBy = \"" + USER_ID + "\" and id = \"" + NOTE_IDS[0] + "\"")
    );
  }

  @BeforeEach
  void setUp() {
    var user = new User(USER_ID, "test_user", null);
    stubUser(user);
    stubConfigurationLimit(defaultNoteTypeLimit);
    databaseHelper.clearTable(TENANT, NOTE);
    databaseHelper.clearTable(TENANT, TYPE);
    databaseHelper.clearTable(TENANT, LINK);
  }

  // Tests for GET
  @Test
  @DisplayName("Find all notes - empty collection")
  void returnEmptyCollection() throws Exception {
    mockMvc.perform(get("/notes").headers(okapiHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes").isEmpty())
      .andExpect(jsonPath("$.totalRecords").value(0));
  }

  @Test
  @DisplayName("Find all notes")
  void returnCollection() throws Exception {
    List<NoteEntity> notes = createListOfNotes();

    mockMvc.perform(get(NOTE_URL).headers(okapiHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0]", not(emptyOrNullString())))
      .andExpect(jsonPath("$.notes.[1]", not(emptyOrNullString())))
      .andExpect(jsonPath("$.notes.[0].title", is(notes.get(0).getTitle())))
      .andExpect(jsonPath("$.notes.[1].title", is(notes.get(1).getTitle())))
      .andExpect(jsonPath("$.totalRecords").value(notes.size()));
  }

  @Test
  @DisplayName("Find all notes with sort by label and limited with offset")
  void returnCollectionSortedByLabelAndLimitedWithOffset() throws Exception {
    List<NoteEntity> notes = createListOfNotes();

    var cqlQuery = "(cql.allRecords=1)sortby title/sort.descending";
    var limit = "1";
    var offset = "1";
    mockMvc.perform(get(NOTE_URL + "?limit={l}&offset={o}&query={cql}", limit, offset, cqlQuery)
        .headers(okapiHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0].title", is(notes.get(1).getTitle())))
      .andExpect(jsonPath("$.notes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(3));
  }

  @Test
  @DisplayName("Find all notes by type id and limit=totalRecords")
  void returnCollectionByTypeIdAndLimitIsEqualToTotalRecords() throws Exception {
    var note = createNote();
    var cqlQuery = "(type.id=" + note.getType().getId() + ")";
    var limit = "1";
    mockMvc.perform(get(NOTE_URL + "?limit={l}&query={cql}", limit, cqlQuery)
        .headers(okapiHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0].title", is(note.getTitle())))
      .andExpect(jsonPath("$.notes.[0].typeId", is(note.getType().getId().toString())))
      .andExpect(jsonPath("$.notes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(1));
  }

  @MethodSource("cqlQueryProvider")
  @ParameterizedTest
  @DisplayName("Find only one note by CQL")
  void returnCollectionByName(String cqlQuery) throws Exception {
    createListOfNotes();
    createLinks(NOTE_IDS[0]);
    mockMvc.perform(get(NOTE_URL + "?query=({cql})", cqlQuery).headers(okapiHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0].id", is(NOTE_IDS[0].toString())))
      .andExpect(jsonPath("$.notes.[0].title", is(NOTE_TITLES[0])))
      .andExpect(jsonPath("$.notes.[0].domain", is(NOTE_DOMAINS[0])))
      .andExpect(jsonPath("$.notes.[0].typeId", is(TYPE_IDS[0].toString())))
      .andExpect(jsonPath("$.notes.[0].type", is(TYPE_NAMES[0])))
      .andExpect(jsonPath("$.notes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(1));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid CQL query")
  void return422OnGetCollectionWithInvalidCqlQuery() throws Exception {
    var cqlQuery = "!invalid-cql!";
    mockMvc.perform(get(NOTE_URL + "?query={cql}", cqlQuery)
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(CqlQueryValidationException.class))
      .andExpect(errorMessageMatch(containsString("Not implemented yet node type")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid offset")
  void return422OnGetCollectionWithInvalidOffset() throws Exception {
    mockMvc.perform(get(NOTE_URL + "?offset={offset}", -1)
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 0")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid limit")
  void return422OnGetCollectionWithInvalidLimit() throws Exception {
    mockMvc.perform(get(NOTE_URL + "?limit={limit}", -1)
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 1")));
  }

  // Tests for POST

  @Test
  @DisplayName("Create new note")
  void createNewNote() throws Exception {
    String title = "First";
    var note = new Note().title(title);

    var noteType = new NoteType().name(randomAlphabetic(100));
    var contentAsString = mockMvc.perform(postNoteType(noteType)).andReturn().getResponse().getContentAsString();
    var existingNoteType = OBJECT_MAPPER.readValue(contentAsString, NoteType.class);

    note.setDomain("Domain");
    note.setTypeId(existingNoteType.getId());

    mockMvc.perform(postNote(note))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.title", is(title)))
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID.toString()))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty())
      .andExpect(header().string(HttpHeaders.LOCATION,
        matchesRegex(
          NOTE_URL + "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")));
    var rowsInTable = databaseHelper.countRowsInTable(TENANT, NOTE);
    assertEquals(1, rowsInTable);
  }

  @Test
  @DisplayName("Return 422 on creation new note when note type is no created")
  void return422onCreationNoteWhenNoteTypeIsNotCreated() throws Exception {
    var note = new Note().title("First").domain("Domain");

    mockMvc.perform(postNote(note))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(MethodArgumentNotValidException.class))
      .andExpect(
        errorMessageMatch(containsString("Field error in object 'note' on field 'typeId': rejected value [null]")));
  }

  @Test
  @DisplayName("Return 422 on creation new note when domain is not set")
  void return422onCreationNoteWhenDomainIsNotSet() throws Exception {
    var note = new Note().title("First").typeId(randomUUID());

    mockMvc.perform(postNote(note))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(MethodArgumentNotValidException.class))
      .andExpect(
        errorMessageMatch(containsString("Field error in object 'note' on field 'domain': rejected value [null]")));
  }

  // Tests for GET by id

  @Test
  @DisplayName("Find note by ID")
  void returnById() throws Exception {
    var note = createNote();

    mockMvc.perform(getById(note.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(note.getId().toString())))
      .andExpect(jsonPath("$.title", is(note.getTitle())))
      .andExpect(jsonPath("$.typeId", is(note.getType().getId().toString())))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty())
      .andExpect(jsonPath("$.metadata.createdByUserId").isNotEmpty())
      .andExpect(jsonPath("$.metadata.createdByUsername").isNotEmpty());
  }

  @ValueSource(ints = {HttpStatus.SC_NOT_FOUND, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_INTERNAL_SERVER_ERROR})
  @ParameterizedTest
  @DisplayName("Find note by ID when user is not exist")
  void returnByIdWhenUserIsNotAvailable(int httpStatus) throws Exception {
    stubUserClientError(httpStatus);
    var note = createNote();

    mockMvc.perform(getById(note.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(note.getId().toString())))
      .andExpect(jsonPath("$.title", is(note.getTitle())))
      .andExpect(jsonPath("$.typeId", is(note.getType().getId().toString())))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty())
      .andExpect(jsonPath("$.metadata.createdByUserId").isNotEmpty())
      .andExpect(jsonPath("$.metadata.createdByUsername").doesNotExist());
  }

  @Test
  @DisplayName("Return 404 on get note by ID when it is not exist")
  void return404OnGetByIdWhenItNotExist() throws Exception {
    mockMvc.perform(getById(randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  @Test
  @DisplayName("Return 422 on get note by ID when id is invalid")
  void return422OnGetByIdWhenIdIsInvalid() throws Exception {
    mockMvc.perform(getById("invalid-uuid"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(MethodArgumentTypeMismatchException.class))
      .andExpect(errorMessageMatch(containsString("Failed to convert value of type")));
  }

  // Tests for PUT

  @Test
  @DisplayName("Update existing note")
  void updateExistingNote() throws Exception {
    var existNote = createNote();
    var links = Collections.singletonList(new Link().id(PACKAGE_ID_1).type(PACKAGE_TYPE));
    var updatedNote = new Note().title("Updated").domain(DOMAIN).typeId(existNote.getType().getId()).links(links);

    mockMvc.perform(putById(existNote.getId(), updatedNote))
      .andExpect(status().isNoContent());

    mockMvc.perform(getById(existNote.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNote.getId().toString())))
      .andExpect(jsonPath("$.title", is(updatedNote.getTitle())))
      .andExpect(jsonPath("$.metadata.updatedByUserId").value(USER_ID.toString()))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Update note type for existing note")
  void updateNoteTypeForExistingNote() throws Exception {
    var existNote = createNote();
    var newNoteType = createNoteType(1);
    var links = Collections.singletonList(new Link().id(PACKAGE_ID_1).type(PACKAGE_TYPE));
    var updatedNote = new Note().title("Updated").domain(DOMAIN).typeId(newNoteType.getId()).links(links);

    mockMvc.perform(putById(existNote.getId(), updatedNote))
      .andExpect(status().isNoContent());

    mockMvc.perform(getById(existNote.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNote.getId().toString())))
      .andExpect(jsonPath("$.title", is(updatedNote.getTitle())))
      .andExpect(jsonPath("$.typeId", is(updatedNote.getTypeId().toString())))
      .andExpect(jsonPath("$.metadata.updatedByUserId").value(USER_ID.toString()))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 422 on update note type for existing note when note type does not exist")
  void return422OnUpdateNoteTypeForExistingNoteWhenNoteTypeDoesNotExist() throws Exception {
    var existNote = createNote();
    var links = Collections.singletonList(new Link().id(PACKAGE_ID_1).type(PACKAGE_TYPE));
    var updatedNote = new Note().title("Updated").domain(DOMAIN).typeId(UUID.randomUUID()).links(links);

    mockMvc.perform(putById(existNote.getId(), updatedNote))
      .andExpect(status().isUnprocessableEntity());

    mockMvc.perform(getById(existNote.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNote.getId().toString())))
      .andExpect(jsonPath("$.title", is(existNote.getTitle())))
      .andExpect(jsonPath("$.typeId", is(existNote.getType().getId().toString())))
      .andExpect(jsonPath("$.metadata.updatedByUserId").doesNotExist())
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 404 on update note by ID when it is not exist")
  void return404OnPutByIdWhenItNotExist() throws Exception {
    var noteType = createNoteType(1);
    var links = Collections.singletonList(new Link().id(PACKAGE_ID_1).type(PACKAGE_TYPE));
    var note = new Note().title("NotExist").domain("Domain").typeId(noteType.getId()).links(links);

    mockMvc.perform(putById(randomUUID(), note))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  // Tests for DELETE

  @Test
  @DisplayName("Delete existing note")
  void deleteExistingNoteType() throws Exception {
    var note = createNote();

    mockMvc.perform(deleteById(note.getId()))
      .andExpect(status().isNoContent());

    var rowsInTable = databaseHelper.countRowsInTable(TENANT, NOTE);
    assertEquals(0, rowsInTable);
  }

  @Test
  @DisplayName("Return 404 on delete note by ID when it is not exist")
  void return404OnDeleteByIdWhenItNotExist() throws Exception {
    mockMvc.perform(deleteById(randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  // Test for links

  @Test
  @DisplayName("Add links to multiple notes")
  void shouldAddLinksToMultipleNotes() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();
    var thirdNote = generateNote();

    createLinks(firstNote.getId(), thirdNote.getId());
    List<Note> notes = getNotes();

    var firstResultNote = getNoteById(notes, firstNote.getId());
    var secondResultNote = getNoteById(notes, secondNote.getId());

    assertTrue(firstResultNote.getLinks().stream().anyMatch(link -> link.getType().equals(PACKAGE_TYPE)));
    assertTrue(firstResultNote.getLinks().stream().anyMatch(link -> link.getId().equals(PACKAGE_ID_1)));
    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());

    var thirdResultNote = getNoteById(notes, thirdNote.getId());

    assertTrue(thirdResultNote.getLinks().stream().anyMatch(link -> link.getType().equals(PACKAGE_TYPE)));
    assertTrue(thirdResultNote.getLinks().stream().anyMatch(link -> link.getId().equals(PACKAGE_ID_1)));
  }

  @Test
  @DisplayName("Remove links from multiple notes")
  void shouldRemoveLinksFromMultipleNotes() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();
    var thirdNote = generateNote();

    createLinks(firstNote.getId(), secondNote.getId(), thirdNote.getId());
    removeLinks(secondNote.getId(), thirdNote.getId());
    List<Note> notes = getNotes();

    var firstResultNote = getNoteById(notes, firstNote.getId());
    var secondResultNote = getNoteById(notes, secondNote.getId());
    var thirdResultNote = getNoteById(notes, thirdNote.getId());

    assertEquals(DEFAULT_LINK_AMOUNT + 1, firstResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, thirdResultNote.getLinks().size());
  }

  @Test
  @DisplayName("Remove and add links to notes")
  void shouldRemoveAndAddLinksToNotes() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();
    var thirdNote = generateNote();
    createLinks(firstNote.getId(), secondNote.getId());

    NoteLinkUpdateCollection noteLinkUpdateCollection = new NoteLinkUpdateCollection()
      .notes(
        Arrays.asList(
          createNoteLink(firstNote.getId(), LinkStatus.UNASSIGNED),
          createNoteLink(secondNote.getId(), LinkStatus.UNASSIGNED),
          createNoteLink(thirdNote.getId(), LinkStatus.ASSIGNED))
      );

    updateLinks(noteLinkUpdateCollection);

    List<Note> notes = getNotes();
    var firstResultNote = getNoteById(notes, firstNote.getId());
    var secondResultNote = getNoteById(notes, secondNote.getId());
    var thirdResultNote = getNoteById(notes, thirdNote.getId());

    assertEquals(DEFAULT_LINK_AMOUNT, firstResultNote.getLinks().size());
    assertEquals(DEFAULT_LINK_AMOUNT, secondResultNote.getLinks().size());
    assertTrue(thirdResultNote.getLinks().stream().anyMatch(link -> link.getType().equals(PACKAGE_TYPE)));
    assertTrue(thirdResultNote.getLinks().stream().anyMatch(link -> link.getId().equals(PACKAGE_ID_1)));
  }

  @Test
  @DisplayName("Shouldn't add link for the second time")
  void shouldNotAddLinkForTheSecondTime() throws Exception {
    var note = generateNote();
    createLinks(note.getId());
    createLinks(note.getId());

    List<Note> notes = getNotes();

    assertEquals(DEFAULT_LINK_AMOUNT + 1, getNoteById(notes, note.getId()).getLinks().size());
  }

  @Test
  @DisplayName("Should ignore second remove request")
  void shouldIgnoreSecondRemoveRequest() throws Exception {
    var note = generateNote();
    createLinks(note.getId());
    removeLinks(note.getId());
    removeLinks(note.getId());

    List<Note> notes = getNotes();

    assertEquals(DEFAULT_LINK_AMOUNT, getNoteById(notes, note.getId()).getLinks().size());
  }

  @Test
  @DisplayName("Should remove note when last link is removed")
  void shouldRemoveNoteWhenLastLinkIsRemoved() throws Exception {
    var note = generateNote()
      .links(Collections.singletonList(new Link()
        .id(PACKAGE_ID_1)
        .type(PACKAGE_TYPE)
      ));

    mockMvc.perform(postNote(note)).andExpect(status().isCreated());
    removeLinks(note.getId());
    List<Note> notes = getNotes();

    assertFalse(notes.stream().anyMatch(resultNote -> note.getId().equals(resultNote.getId())));
  }

  @Test
  @DisplayName("Should do nothing on empty list")
  void shouldDoNothingOnEmptyList() throws Exception {
    removeLinks();

    List<Note> notes = getNotes();

    assertTrue(notes.isEmpty());
  }

  @Test
  @DisplayName("Should return list of notes without parameters")
  void shouldReturnListOfNotesWithoutParameters() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class);

    assertEquals(2, notes.getTotalRecords());
  }

  @Test
  @DisplayName("Should return empty note collection")
  void shouldReturnEmptyNoteCollection() throws Exception {
    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/123-456789");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class);

    assertEquals(0, notes.getTotalRecords());
  }

  @Test
  @DisplayName("Should return list of notes with limit")
  void shouldReturnListOfNotesWithLimit() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/123-456789?limit=1");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class);

    assertEquals(1, notes.getNotes().size());
    assertEquals(2, notes.getTotalRecords());
  }

  @Test
  @DisplayName("Should return list of notes with offset")
  void shouldReturnListOfNotesWithOffset() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/123-456789?offset=1");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  @DisplayName("Should return all records of notes from DB without parameters and with non existing id")
  void shouldReturnAllRecordsOfNotesFromDbWithoutParametersAndWithNonExistingId() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/"
      + NON_EXISTING_ID);
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  @DisplayName("Should return all records of notes from DB without parameters and with incomplete url")
  void shouldReturnAllRecordsOfNotesFromDbWithoutParametersAndWithIncompleteUrl() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/"
      + PACKAGE_ID_1 + "?orde");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
  }

  @Test
  @DisplayName("Should return list of notes with assigned status")
  void shouldReturnListOfNotesWithAssignedStatus() throws Exception {
    var firstNote = generateNote();
    generateNote();
    createLinks(firstNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?status=ASSIGNED");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  @DisplayName("Should return list of notes sorted by title asc")
  void shouldReturnListOfNotesSortedByTitleAsc() throws Exception {
    var firstNote = generateNote().title("ABC");
    var secondNote = generateNote().title("ZZZ");

    mockMvc.perform(putById(firstNote.getId(), firstNote)).andExpect(status().isNoContent());
    mockMvc.perform(putById(secondNote.getId(), secondNote)).andExpect(status().isNoContent());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?order=asc&orderBy=title");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getTitle(), notes.get(0).getTitle());
    assertEquals(secondNote.getTitle(), notes.get(1).getTitle());
  }

  @Test
  @DisplayName("Should return list of notes sorted by title desc")
  void shouldReturnListOfNotesSortedByTitleDesc() throws Exception {
    var firstNote = generateNote().title("ABC");
    var secondNote = generateNote().title("ZZZ");

    mockMvc.perform(putById(firstNote.getId(), firstNote)).andExpect(status().isNoContent());
    mockMvc.perform(putById(secondNote.getId(), secondNote)).andExpect(status().isNoContent());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?order=desc&orderBy=title");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(secondNote.getTitle(), notes.get(0).getTitle());
    assertEquals(firstNote.getTitle(), notes.get(1).getTitle());
  }

  @Test
  @DisplayName("Should return list of notes sorted by note type asc")
  void shouldReturnListOfNotesSortedByNoteTypeAsc() throws Exception {
    var firstNote = generateNoteEntityWithParams("ABC", "13f21797-d25b-46dc-8427-1759d1db2057", randomAlphabetic(100));
    var secondNote = generateNoteEntityWithParams("XWZ", "2af21797-d25b-46dc-8427-1759d1db2057", randomAlphabetic(100));

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?orderBy=noteType&order=asc");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getType().getId(), notes.get(0).getTypeId());
    assertEquals(secondNote.getType().getId(), notes.get(1).getTypeId());
  }

  @Test
  @DisplayName("Should return list of notes sorted by note type desc")
  void shouldReturnListOfNotesSortedByNoteTypeDesc() throws Exception {
    var firstNote = generateNoteEntityWithParams("ABC", "2af21797-d25b-46dc-8427-1759d1db2057", randomAlphabetic(100));
    var secondNote = generateNoteEntityWithParams("XWZ", "13f21797-d25b-46dc-8427-1759d1db2057", randomAlphabetic(100));

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?orderBy=noteType&order=desc");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getType().getId(), notes.get(0).getTypeId());
    assertEquals(secondNote.getType().getId(), notes.get(1).getTypeId());
  }

  @Test
  @DisplayName("Should return 400 when order parameter is invalid")
  void shouldReturn400WhenOrderParameterIsInvalid() throws Exception {
    generateNote();
    generateNote();

    mockMvc.perform(get("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
        + "?orderBy=noteype&order=desc")
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatch(containsString("Failed to convert value of type 'java.lang.String' to required "
        + "type 'org.folio.notes.domain.dto.NotesOrderBy'")));
  }

  @ParameterizedTest
  @CsvSource({
    "ABC, 2, XYZ, 1, asc",
    "ABC, 2, XYZ, 1, desc",
    "ABC, 1, ABC, 2, asc",
    "ABC, 1, ABC, 2, desc"})
  @DisplayName("should return list of notes sorted by content")
  void shouldReturnListOfNotesSortedByContent(String firstTitle, String firstContent,
                                              String secondTitle, String secondContent,
                                              String sortDirection) throws Exception {
    Function<String, String> contentFormat = content ->
      String.format("<div> <strong>%s</strong></div><h1>thing</h1>", content);
    var firstNote = generateNote()
      .title(firstTitle)
      .content(contentFormat.apply(firstContent));
    var secondNote = generateNote()
      .title(secondTitle)
      .content(contentFormat.apply(secondContent));

    mockMvc.perform(putById(firstNote.getId(), firstNote)).andExpect(status().isNoContent());
    mockMvc.perform(putById(secondNote.getId(), secondNote)).andExpect(status().isNoContent());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?orderBy=content&order=" + sortDirection);
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());

    var actualFirstNoteId = notes.get(0).getId();
    if (sortDirection.equals("asc")) {
      assertEquals(firstNote.getId(), actualFirstNoteId);
    } else {
      assertEquals(secondNote.getId(), actualFirstNoteId);
    }
  }

  @Test
  @DisplayName("should return list of notes sorted by created date asc")
  void shouldReturnListOfNotesSortedByCreatedDateAsc() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();

    mockMvc.perform(postNote(firstNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(secondNote)).andExpect(status().isCreated());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?orderBy=updatedDate&order=asc");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertTrue(notes.get(0).getMetadata().getUpdatedDate().isBefore(notes.get(1).getMetadata().getUpdatedDate()));
  }

  @Test
  @DisplayName("should return list of notes sorted by created date desc")
  void shouldReturnListOfNotesSortedByCreatedDateDesc() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();

    mockMvc.perform(postNote(firstNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(secondNote)).andExpect(status().isCreated());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?orderBy=updatedDate");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertTrue(notes.get(0).getMetadata().getUpdatedDate().isAfter(notes.get(1).getMetadata().getUpdatedDate()));
  }

  @Test
  @DisplayName("should return 400 when order by updated date parameter is invalid")
  void shouldReturn400WhenOrderByUpdatedDateParameterIsInvalid() throws Exception {
    generateNote();
    generateNote();

    mockMvc.perform(get("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1 + 1
        + "?orderBy=u&order=desc")
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatch(containsString("Failed to convert value of type 'java.lang.String' "
        + "to required type 'org.folio.notes.domain.dto.NotesOrderBy'")));
  }

  @Test
  @DisplayName("Should return list of notes searched by content case insensitively")
  void shouldReturnListOfNotesSearchedByContent() throws Exception {
    var firstNote = generateNote().title("Title ABC").content("<p>test content</p><p>zztest</p>");
    var secondNote = generateNote().title("Title ZZZ ABC");
    var thirdNote = generateNote().title("Title TTT");

    mockMvc.perform(putById(firstNote.getId(), firstNote)).andExpect(status().isNoContent());
    mockMvc.perform(putById(secondNote.getId(), secondNote)).andExpect(status().isNoContent());
    mockMvc.perform(putById(thirdNote.getId(), thirdNote)).andExpect(status().isNoContent());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());
    createLinks(thirdNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?search=ZZ&orderBy=content");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getTitle(), notes.get(0).getTitle());
    assertEquals(secondNote.getTitle(), notes.get(1).getTitle());
  }

  @Test
  @DisplayName("Should interpret special regex characters literally")
  void shouldInterpretSpecialRegexCharactersLiterally() throws Exception {
    var firstNote = generateNote().title("a[abc1}{]z");
    mockMvc.perform(postNote(firstNote)).andExpect(status().isCreated());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?search=a[abc1}{]z");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(1, notes.size());
    assertEquals(firstNote.getTitle(), notes.get(0).getTitle());
  }

  @Test
  @DisplayName("Should return list of assigned notes searched and sorted by title")
  void shouldReturnListOfAssignedNotesSearchedAndSortedByTitle() throws Exception {
    var firstNote = generateNote().title("Title ABC");
    var secondNote = generateNote().title("Title ZZZ ABC");
    var thirdNote = generateNote().title("Title BBB");

    mockMvc.perform(postNote(firstNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(secondNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(thirdNote)).andExpect(status().isCreated());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?status=ASSIGNED&orderBy=title");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(firstNote.getTitle(), notes.get(0).getTitle());
  }

  @Test
  @DisplayName("Should return list of assigned notes searched and sorted by title order desc")
  void shouldReturnListOfAssignedNotesSearchedAndSortedByTitleOrderDesc() throws Exception {
    var firstNote = generateNote().title("Title ABC");
    var secondNote = generateNote().title("Title ZZZ ABC");
    var thirdNote = generateNote().title("Title BBB");

    mockMvc.perform(postNote(firstNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(secondNote)).andExpect(status().isCreated());
    mockMvc.perform(postNote(thirdNote)).andExpect(status().isCreated());

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?status=ASSIGNED&orderBy=title&order=desc");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());
    assertEquals(secondNote.getTitle(), notes.get(0).getTitle());
  }

  @Test
  @DisplayName("Should return list of notes with unassigned status")
  void shouldReturnListOfNotesWithUnassignedStatus() throws Exception {
    var firsNoteWithAssignedLink = generateNote();
    generateNote();
    createLinks(firsNoteWithAssignedLink.getId());

    var content = getNoteLinks("/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1
      + "?status=UNASSIGNED");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(1, notes.size());
  }

  @Test
  @DisplayName("Should return empty list of notes with non existing domain")
  void shouldReturnEmptyListOfNotesWithNonExistingDomain() throws Exception {
    generateNote();
    generateNote();

    var content = getNoteLinks("/note-links/domain/" + NON_EXISTING_DOMAIN + "/type/" + PACKAGE_TYPE
      + "/id/" + PACKAGE_ID_1 + "?status=UNASSIGNED");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(0, notes.size());
  }

  @Test
  @DisplayName("Should return list of notes with status all")
  void shouldReturnListOfNotesWithStatusAll() throws Exception {
    var firstNote = generateNote();
    var secondNote = generateNote();

    createLinks(firstNote.getId());

    var url = "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_1 + "?status=";
    var content = getNoteLinks(url + "all");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertEquals(2, notes.size());

    var contentWithUnAssignedLink = getNoteLinks(url + LinkStatus.UNASSIGNED);
    var notesWithUnAssignedLink = OBJECT_MAPPER.readValue(contentWithUnAssignedLink, NoteCollection.class).getNotes();

    assertEquals(secondNote.getId(), notesWithUnAssignedLink.get(0).getId());
    assertEquals(1, notesWithUnAssignedLink.size());

    var contentWithAssignedLink = getNoteLinks(url + LinkStatus.ASSIGNED);
    var notesWithAssignedLink = OBJECT_MAPPER.readValue(contentWithAssignedLink, NoteCollection.class).getNotes();

    assertEquals(firstNote.getId(), notesWithAssignedLink.get(0).getId());
    assertEquals(1, notesWithAssignedLink.size());
  }

  @Test
  @DisplayName("Should return note list when search by title and note type")
  void shouldReturnNoteListWhenSearchByTitleAndNoteType() throws Exception {
    var noteTitle = "testNote";
    var firstNote = generateNoteEntityWithParams(noteTitle, NOTE_TYPE_ID_1, NOTE_TYPE_NAME_2);
    var secondNote = generateNoteEntityWithParams(noteTitle, NOTE_TYPE_ID_2, NOTE_TYPE_NAME_1);

    createLinks(firstNote.getId());
    createLinks(secondNote.getId());

    var content = getNoteLinks(
      "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE + "/id/" + PACKAGE_ID_2
        + "?search=" + noteTitle + "&noteType=" + NOTE_TYPE_NAME_1 + "&order=ASC");
    var notes = OBJECT_MAPPER.readValue(content, NoteCollection.class).getNotes();

    assertThat(notes.size(), equalTo(1));
    assertThat(notes.get(0).getTypeId(), equalTo(UUID.fromString(NOTE_TYPE_ID_2)));
    assertThat(notes.get(0).getTitle(), equalTo(noteTitle));
  }

  @Test
  @DisplayName("Should return 400 with error message wrong order")
  void shouldReturn400WithErrorMessageWrongOrder() throws Exception {
    generateNote();
    generateNote();

    mockMvc.perform(get(
        "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
          + "/id/" + PACKAGE_ID_1 + "?order=wrong")
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatch(containsString("Failed to convert value of type 'java.lang.String' "
        + "to required type 'org.folio.notes.domain.dto.OrderDirection")));
  }

  @Test
  @DisplayName("Should return 400 with error message wrong limit and offset")
  void shouldReturn400WithErrorMessageWrongLimitAndOffset() throws Exception {
    generateNote();
    generateNote();

    mockMvc.perform(get(
        "/note-links/domain/" + DOMAIN + "/type/" + PACKAGE_TYPE
          + "/id/" + PACKAGE_ID_1 + "?limit=-1&offset=-1")
        .headers(okapiHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(errorMessageMatch(containsString("getNoteCollectionByLink.offset: must be greater "
        + "than or equal to 0")))
      .andExpect(errorMessageMatch(containsString("getNoteCollectionByLink.limit: must be greater "
        + "than or equal to 1")));
  }

  private NoteEntity generateNoteEntityWithParams(String title, String typeId, String noteName) {
    var noteType = new NoteTypeEntity();
    noteType.setId(UUID.fromString(typeId));
    noteType.setName(noteName);
    databaseHelper.saveNoteType(noteType, TENANT);
    var noteEntity = new NoteEntity();
    noteEntity.setId(randomUUID());
    noteEntity.setTitle(title);
    noteEntity.setContent("");
    noteEntity.setDomain(DOMAIN);
    noteEntity.setType(noteType);
    databaseHelper.saveNote(noteEntity, TENANT);
    return noteEntity;
  }

  private Note generateNote() throws Exception {
    var noteType = new NoteType().name(randomAlphabetic(100));
    var notyTypeAsString = mockMvc.perform(postNoteType(noteType)).andReturn().getResponse().getContentAsString();
    var existingNoteType = OBJECT_MAPPER.readValue(notyTypeAsString, NoteType.class);
    var link = new Link().id(LINK_ID).type(DOMAIN);
    var note = new Note().title(NOTE_TITLES[0]).domain(DOMAIN).typeId(existingNoteType.getId())
      .links(Collections.singletonList(link));
    var noteAsString = mockMvc.perform(postNote(note)).andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return OBJECT_MAPPER.readValue(noteAsString, Note.class);
  }

  private Note getNoteById(List<Note> notes, UUID id) {
    return notes.stream()
      .filter(note -> note.getId().equals(id))
      .findFirst().orElse(null);
  }

  private void createLinks(UUID... ids) throws Exception {
    changeLinks(ids, LinkStatus.ASSIGNED);
  }

  private void removeLinks(UUID... ids) throws Exception {
    changeLinks(ids, LinkStatus.UNASSIGNED);
  }

  private void changeLinks(UUID[] ids, LinkStatus status) throws Exception {
    NoteLinkUpdateCollection noteLinkUpdateCollection = createNoteLinkUpdateCollection(status, ids);
    updateLinks(noteLinkUpdateCollection);
  }

  private void updateLinks(NoteLinkUpdateCollection noteLinkUpdateCollection) throws Exception {
    mockMvc.perform(updateLink(noteLinkUpdateCollection));
  }

  private String getNoteLinks(String url) throws Exception {
    return mockMvc.perform(get(url)
        .headers(okapiHeaders()))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private MockHttpServletRequestBuilder updateLink(NoteLinkUpdateCollection noteLinkUpdateCollection) {
    return put(NOTE_LINKS_PATH)
      .content(asJsonString(noteLinkUpdateCollection))
      .headers(okapiHeaders());
  }

  private NoteLinkUpdateCollection createNoteLinkUpdateCollection(LinkStatus status, UUID[] ids) {
    return new NoteLinkUpdateCollection()
      .notes(
        Arrays.stream(ids)
          .map(id -> createNoteLink(id, status))
          .collect(Collectors.toList())
      );
  }

  private NoteLinkUpdate createNoteLink(UUID id, LinkStatus status) {
    return new NoteLinkUpdate()
      .id(id)
      .status(status);
  }

  private List<Note> getNotes() throws Exception {
    var contentAsString = getNoteLinks(NOTE_URL);
    return OBJECT_MAPPER.readValue(contentAsString, NoteCollection.class).getNotes();
  }

  private List<NoteEntity> createListOfNotes() {
    List<NoteEntity> notes = List.of(
      prepareNote(0),
      prepareNote(1),
      prepareNote(2)
    );
    databaseHelper.saveNotes(notes, TENANT);
    return notes;
  }

  private NoteEntity createNote() {
    var noteEntity = prepareNote(0);
    databaseHelper.saveNote(noteEntity, TENANT);
    return noteEntity;
  }

  private NoteEntity prepareNote(int noteNum) {
    var noteEntity = new NoteEntity();
    noteEntity.setId(NOTE_IDS[noteNum]);
    noteEntity.setTitle(NOTE_TITLES[noteNum]);
    noteEntity.setDomain(NOTE_DOMAINS[noteNum]);
    noteEntity.setContent(NOTE_CONTENT[noteNum]);
    noteEntity.setType(createNoteType(noteNum));
    noteEntity.setCreatedBy(USER_ID);
    return noteEntity;
  }

  private NoteTypeEntity createNoteType(int noteNum) {
    int typeId = Math.min(noteNum, TYPE_IDS.length - 1);
    var noteTypeById = databaseHelper.getNoteTypeById(TYPE_IDS[typeId], TENANT);
    if (noteTypeById == null) {
      NoteTypeEntity noteType = new NoteTypeEntity();
      noteType.setId(TYPE_IDS[typeId]);
      noteType.setName(TYPE_NAMES[typeId]);

      databaseHelper.saveNoteType(noteType, TENANT);
      return noteType;
    }
    return noteTypeById;
  }

  private MockHttpServletRequestBuilder postNote(Note note) {
    return post(NOTE_URL)
      .headers(okapiHeaders())
      .content(asJsonString(note));
  }

  private MockHttpServletRequestBuilder postNoteType(NoteType noteType) {
    return post(NOTE_TYPE_URL)
      .headers(okapiHeaders())
      .content(asJsonString(noteType));
  }

  private MockHttpServletRequestBuilder getById(Object id) {
    return get(NOTE_URL + "/{id}", id)
      .headers(okapiHeaders());
  }

  private MockHttpServletRequestBuilder putById(UUID id, Note note) {
    return put(NOTE_URL + "/{id}", id)
      .content(asJsonString(note))
      .headers(okapiHeaders());
  }

  private MockHttpServletRequestBuilder deleteById(UUID id) {
    return delete(NOTE_URL + "/{id}", id)
      .headers(okapiHeaders());
  }

  private ResultMatcher errorMessageMatch(Matcher<String> errorMessageMatcher) {
    return jsonPath("$.errors.[0].message", errorMessageMatcher);
  }

  private <T> ResultMatcher exceptionMatch(Class<T> type) {
    return result -> assertThat(result.getResolvedException(), instanceOf(type));
  }
}

