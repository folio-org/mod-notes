package org.folio.notes.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.folio.notes.support.DatabaseHelper.NOTE;
import static org.folio.notes.support.DatabaseHelper.TYPE;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.validation.ConstraintViolationException;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

import org.folio.notes.client.UsersClient;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.User;
import org.folio.notes.domain.entity.NoteEntity;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.exception.NoteNotFoundException;
import org.folio.notes.support.TestApiBase;
import org.folio.spring.cql.CqlQueryValidationException;

class NoteControllerTest extends TestApiBase {

  private static final String NOTE_URL = "/notes";
  private static final String NOTE_TYPE_URL = "/note-types";
  private static final String DOMAIN = "domain";

  @Value("${folio.notes.types.defaults.limit}")
  private String defaultNoteTypeLimit;

  @MockBean
  private UsersClient client;

  @BeforeEach
  void setUp() {
    var user = new User(UUID.randomUUID(), "test_user", null);
    when(client.fetchUserById(USER_ID)).thenReturn(Optional.of(user));
    setUpConfigurationLimit(defaultNoteTypeLimit);
    databaseHelper.clearTable(TENANT, NOTE);
    databaseHelper.clearTable(TENANT, TYPE);
  }

  // Tests for GET

  @Test
  @DisplayName("Find all notes - empty collection")
  void returnEmptyCollection() throws Exception {
    mockMvc.perform(get("/notes").headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes").doesNotHaveJsonPath())
      .andExpect(jsonPath("$.totalRecords").value(0));
  }

  @Test
  @DisplayName("Find all notes")
  void returnCollection() throws Exception {
    List<NoteEntity> notes = createListOfNotes();

    mockMvc.perform(get(NOTE_URL).headers(defaultHeaders()))
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
        .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0].title", is(notes.get(1).getTitle())))
      .andExpect(jsonPath("$.notes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(3));
  }

  @Test
  @DisplayName("Find all notes by title")
  void returnCollectionByName() throws Exception {
    List<NoteEntity> notes = createListOfNotes();

    var cqlQuery = "title=third";
    mockMvc.perform(get(NOTE_URL + "?query={cql}", cqlQuery).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.notes.[0].title", is(notes.get(2).getTitle())))
      .andExpect(jsonPath("$.notes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(1));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid CQL query")
  void return422OnGetCollectionWithInvalidCqlQuery() throws Exception {
    var cqlQuery = "!invalid-cql!";
    mockMvc.perform(get(NOTE_URL + "?query={cql}", cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(CqlQueryValidationException.class))
      .andExpect(errorMessageMatch(containsString("Not implemented yet node type")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid offset")
  void return422OnGetCollectionWithInvalidOffset() throws Exception {
    mockMvc.perform(get(NOTE_URL + "?offset={offset}", -1)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 0")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid limit")
  void return422OnGetCollectionWithInvalidLimit() throws Exception {
    mockMvc.perform(get(NOTE_URL + "?limit={limit}", -1)
        .headers(defaultHeaders()))
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

    var noteType = new NoteType().name(RandomStringUtils.randomAlphabetic(100));
    var contentAsString = mockMvc.perform(postNoteType(noteType)).andReturn().getResponse().getContentAsString();
    var existingNoteType = OBJECT_MAPPER.readValue(contentAsString, NoteType.class);

    note.setDomain("Domain");
    note.setTypeId(existingNoteType.getId());

    mockMvc.perform(postNote(note))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.title", is(title)))
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID))
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
      .andExpect(errorMessageMatch(containsString("Field error in object 'note' on field 'typeId': rejected value [null]")));
  }

  @Test
  @DisplayName("Return 422 on creation new note when domain is not set")
  void return422onCreationNoteWhenDomainIsNotSet() throws Exception {
    var note = new Note().title("First").typeId(UUID.randomUUID());

    mockMvc.perform(postNote(note))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(MethodArgumentNotValidException.class))
      .andExpect(errorMessageMatch(containsString("Field error in object 'note' on field 'domain': rejected value [null]")));
  }


  // Tests for GET by id

  @Test
  @DisplayName("Find note by ID")
  void returnById() throws Exception {
    var note = createNote("NoteById");

    mockMvc.perform(getById(note.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(note.getId().toString())))
      .andExpect(jsonPath("$.title", is(note.getTitle())))
      .andExpect(jsonPath("$.typeId", is(note.getType().getId().toString())))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 404 on get note by ID when it is not exist")
  void return404OnGetByIdWhenItNotExist() throws Exception {
    mockMvc.perform(getById(UUID.randomUUID()))
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
  void updateExistingNoteType() throws Exception {
    var existNote = createNote("Exist");

    var updatedNote = new Note();
    updatedNote.setTitle("Updated");
    updatedNote.setDomain("Domain");
    updatedNote.setTypeId(existNote.getType().getId());

    mockMvc.perform(putById(existNote.getId(), updatedNote))
      .andExpect(status().isNoContent());

    mockMvc.perform(getById(existNote.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNote.getId().toString())))
      .andExpect(jsonPath("$.title", is(updatedNote.getTitle())))
      .andExpect(jsonPath("$.metadata.updatedByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 404 on update note by ID when it is not exist")
  void return404OnPutByIdWhenItNotExist() throws Exception {
    var noteType = createNoteType();
    var note = new Note().title("NotExist").domain("Domain").typeId(noteType.getId());

    mockMvc.perform(putById(UUID.randomUUID(), note))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  // Tests for DELETE

  @Test
  @DisplayName("Delete existing note")
  void deleteExistingNoteType() throws Exception {
    var note = createNote("ToDelete");

    mockMvc.perform(deleteById(note.getId()))
      .andExpect(status().isNoContent());

    var rowsInTable = databaseHelper.countRowsInTable(TENANT, NOTE);
    assertEquals(0, rowsInTable);
  }

  @Test
  @DisplayName("Return 404 on delete note by ID when it is not exist")
  void return404OnDeleteByIdWhenItNotExist() throws Exception {
    mockMvc.perform(deleteById(UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  private List<NoteEntity> createListOfNotes() {
    List<NoteEntity> notes = List.of(
      populateNote("first"),
      populateNote("second"),
      populateNote("third")
    );
    databaseHelper.saveNotes(notes, TENANT);
    return notes;
  }

  private NoteEntity createNote(String title) {
    var noteEntity = populateNote(title);
    databaseHelper.saveNote(noteEntity, TENANT);
    return noteEntity;
  }

  private NoteEntity populateNote(String title) {
    var noteEntity = new NoteEntity();
    noteEntity.setId(UUID.randomUUID());
    noteEntity.setTitle(title);
    noteEntity.setDomain(DOMAIN);
    noteEntity.setType(createNoteType());
    return noteEntity;
  }

  private NoteTypeEntity createNoteType() {
    NoteTypeEntity noteType = new NoteTypeEntity();
    noteType.setId(UUID.randomUUID());
    noteType.setName(RandomStringUtils.randomAlphabetic(100));

    databaseHelper.saveNoteType(noteType, TENANT);
    return noteType;
  }

  private MockHttpServletRequestBuilder postNote(Note note) {
    return post(NOTE_URL)
      .headers(defaultHeaders())
      .content(asJsonString(note));
  }

  private MockHttpServletRequestBuilder postNoteType(NoteType noteType) {
    return post(NOTE_TYPE_URL)
      .headers(defaultHeaders())
      .content(asJsonString(noteType));
  }

  private MockHttpServletRequestBuilder getById(Object id) {
    return get(NOTE_URL + "/{id}", id)
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder putById(UUID id, Note note) {
    return put(NOTE_URL + "/{id}", id)
      .content(asJsonString(note))
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder deleteById(UUID id) {
    return delete(NOTE_URL + "/{id}", id)
      .headers(defaultHeaders());
  }

  private ResultMatcher errorMessageMatch(Matcher<String> errorMessageMatcher) {
    return jsonPath("$.errors.[0].message", errorMessageMatcher);
  }

  private <T> ResultMatcher exceptionMatch(Class<T> type) {
    return result -> assertThat(result.getResolvedException(), instanceOf(type));
  }
}

