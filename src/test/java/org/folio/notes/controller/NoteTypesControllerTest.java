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

import static org.folio.notes.support.DatabaseHelper.TYPE;

import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

import org.folio.notes.client.UsersClient;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.User;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.notes.exception.NoteTypeNotFoundException;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.support.TestApiBase;
import org.folio.spring.cql.CqlQueryValidationException;

class NoteTypesControllerTest extends TestApiBase {

  private static final String BASE_URL = "/note-types";
  private static final String NOTE_URL = "/notes";

  @MockBean
  private UsersClient client;

  @Value("${folio.notes.types.defaults.limit}")
  private String defaultNoteTypeLimit;

  @BeforeEach
  void setUp() {
    var user = new User(UUID.randomUUID(), "test_user", null);
    when(client.fetchUserById(USER_ID)).thenReturn(Optional.of(user));
    setUpConfigurationLimit(defaultNoteTypeLimit);
    databaseHelper.clearTable(TENANT, TYPE);
  }

  // Tests for GET

  @Test
  @DisplayName("Find all note-types - empty collection")
  void returnEmptyCollection() throws Exception {
    mockMvc.perform(get("/note-types").headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.noteTypes").isEmpty())
      .andExpect(jsonPath("$.totalRecords").value(0));
  }

  @Test
  @DisplayName("Find all note-types")
  void returnCollection() throws Exception {
    List<NoteTypeEntity> noteTypes = createListOfNoteTypes();

    mockMvc.perform(get(BASE_URL).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.noteTypes.[0]", not(emptyOrNullString())))
      .andExpect(jsonPath("$.noteTypes.[1]", not(emptyOrNullString())))
      .andExpect(jsonPath("$.noteTypes.[0].name", is(noteTypes.get(0).getName())))
      .andExpect(jsonPath("$.noteTypes.[1].name", is(noteTypes.get(1).getName())))
      .andExpect(jsonPath("$.totalRecords").value(noteTypes.size()));
  }

  @Test
  @DisplayName("Find all note-types with note usage")
  void returnCollectionWithNoteUsage() throws Exception {
    var noteFirst = new Note().title("First");
    var noteSecond = new Note().title("Second");
    var noteThird = new Note().title("Third");

    generateNoteType(Arrays.asList(noteFirst, noteSecond));
    generateNoteType(Collections.singletonList(noteThird));
    generateNoteType(Arrays.asList(noteFirst, noteSecond, noteThird));

    mockMvc.perform(get(BASE_URL).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.noteTypes.[0]", not(emptyOrNullString())))
      .andExpect(jsonPath("$.noteTypes.[0].usage.noteTotal", is(2)))
      .andExpect(jsonPath("$.noteTypes.[1].usage.noteTotal", is(1)))
      .andExpect(jsonPath("$.noteTypes.[2].usage.noteTotal", is(3)))
      .andExpect(jsonPath("$.totalRecords").value(3));
  }

  @Test
  @DisplayName("Find all note-types with sort by label and limited with offset")
  void returnCollectionSortedByLabelAndLimitedWithOffset() throws Exception {
    List<NoteTypeEntity> noteTypes = createListOfNoteTypes();

    var cqlQuery = "(cql.allRecords=1)sortby name/sort.descending";
    var limit = "1";
    var offset = "1";
    mockMvc.perform(get(BASE_URL + "?limit={l}&offset={o}&query={cql}", limit, offset, cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.noteTypes.[0].name", is(noteTypes.get(1).getName())))
      .andExpect(jsonPath("$.noteTypes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(3));
  }

  @Test
  @DisplayName("Find all note-types by name")
  void returnCollectionByName() throws Exception {
    List<NoteTypeEntity> noteTypes = createListOfNoteTypes();

    var cqlQuery = "name=third";
    mockMvc.perform(get(BASE_URL + "?query={cql}", cqlQuery).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.noteTypes.[0].name", is(noteTypes.get(2).getName())))
      .andExpect(jsonPath("$.noteTypes.[1]").doesNotExist())
      .andExpect(jsonPath("$.totalRecords").value(1));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid CQL query")
  void return422OnGetCollectionWithInvalidCqlQuery() throws Exception {
    var cqlQuery = "!invalid-cql!";
    mockMvc.perform(get(BASE_URL + "?query={cql}", cqlQuery)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(CqlQueryValidationException.class))
      .andExpect(errorMessageMatch(containsString("Not implemented yet node type")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid offset")
  void return422OnGetCollectionWithInvalidOffset() throws Exception {
    mockMvc.perform(get(BASE_URL + "?offset={offset}", -1)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 0")));
  }

  @Test
  @DisplayName("Return 422 on get collection with invalid limit")
  void return422OnGetCollectionWithInvalidLimit() throws Exception {
    mockMvc.perform(get(BASE_URL + "?limit={limit}", -1)
        .headers(defaultHeaders()))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(ConstraintViolationException.class))
      .andExpect(errorMessageMatch(containsString("must be greater than or equal to 1")));
  }

  // Tests for POST

  @Test
  @DisplayName("Create new note-type")
  void createNewNoteType() throws Exception {
    String name = "First";
    NoteType noteType = new NoteType().name(name);

    mockMvc.perform(postNoteType(noteType))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name", is(name)))
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty())
      .andExpect(header().string(HttpHeaders.LOCATION,
        matchesRegex(
          BASE_URL + "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")));

    int rowsInTable = databaseHelper.countRowsInTable(TENANT, TYPE);
    assertEquals(1, rowsInTable);
  }

  @Test
  @DisplayName("Create new note-type with use default limit if config doesn't exist")
  void createNewNoteTypeWithUseDefaultLimit() throws Exception {
    setUpConfigurationLimit(" ");

    String name = "First";
    NoteType noteType = new NoteType().name(name);

    mockMvc.perform(postNoteType(noteType))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name", is(name)))
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());

    int rowsInTable = databaseHelper.countRowsInTable(TENANT, TYPE);
    assertEquals(1, rowsInTable);
  }

  @Test
  @DisplayName("Create new note-type with use default limit if config returns error")
  void createNewNoteTypeWithUseDefaultLimitIfConfigReturnsError() throws Exception {
    stubConfigurationClientError(400, "Required permission: get-configuration");

    String name = "First";
    NoteType noteType = new NoteType().name(name);

    mockMvc.perform(postNoteType(noteType))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.name", is(name)))
      .andExpect(jsonPath("$.metadata.createdByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());

    int rowsInTable = databaseHelper.countRowsInTable(TENANT, TYPE);
    assertEquals(1, rowsInTable);
  }

  @Test
  @DisplayName("Return 422 on post note-type with duplicate name")
  void return422OnPostWithDuplicateName() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("DuplicateName");

    NoteType duplicateNoteType = new NoteType().name(existNoteType.getName());

    mockMvc.perform(postNoteType(duplicateNoteType))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(DataIntegrityViolationException.class))
      .andExpect(errorMessageMatch(containsString("Key (name)=(DuplicateName) already exists")));
  }

  @Test
  @DisplayName("Return 422 on post note-type with limit reached")
  void return422OnPostWithLimitReached() throws Exception {
    String limit = "1";
    setUpConfigurationLimit(limit);

    NoteTypeEntity existNoteType = createNoteType("LimitReached");

    NoteType duplicateNoteType = new NoteType().name(existNoteType.getName());

    mockMvc.perform(postNoteType(duplicateNoteType))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(NoteTypesLimitReached.class))
      .andExpect(errorMessageMatch(containsString("Maximum number of note types allowed is " + limit)));

    int rowsInTable = databaseHelper.countRowsInTable(TENANT, TYPE);
    assertEquals(Integer.parseInt(limit), rowsInTable);
  }

  // Tests for GET by id

  @Test
  @DisplayName("Find note-type by ID")
  void returnById() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("ById");

    mockMvc.perform(getById(existNoteType.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNoteType.getId().toString())))
      .andExpect(jsonPath("$.name", is(existNoteType.getName())))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Find note-type with note usage by ID")
  void returnNoteTypeWithNoteUsageById() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("ById");
    var noteFirst = new Note().title("First").typeId(existNoteType.getId()).domain("domain");
    var noteSecond = new Note().title("Second").typeId(existNoteType.getId()).domain("domain");

    mockMvc.perform(postNote(noteFirst))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.title", is(noteFirst.getTitle())));

    mockMvc.perform(postNote(noteSecond))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.title", is(noteSecond.getTitle())));

    mockMvc.perform(getById(existNoteType.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNoteType.getId().toString())))
      .andExpect(jsonPath("$.name", is(existNoteType.getName())))
      .andExpect(jsonPath("$.usage.noteTotal", is(2)))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 404 on get note-type by ID when it is not exist")
  void return404OnGetByIdWhenItNotExist() throws Exception {
    mockMvc.perform(getById(UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteTypeNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  @Test
  @DisplayName("Return 422 on get note-type by ID when id is invalid")
  void return422OnGetByIdWhenIdIsInvalid() throws Exception {
    mockMvc.perform(getById("invalid-uuid"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(MethodArgumentTypeMismatchException.class))
      .andExpect(errorMessageMatch(containsString("Failed to convert value of type")));
  }

  // Tests for PUT

  @Test
  @DisplayName("Update existing note-type")
  void updateExistingNoteType() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("Exist");

    NoteType updatedNoteType = new NoteType();
    updatedNoteType.setName("Updated");

    mockMvc.perform(putById(existNoteType.getId(), updatedNoteType))
      .andExpect(status().isNoContent());

    mockMvc.perform(getById(existNoteType.getId()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(existNoteType.getId().toString())))
      .andExpect(jsonPath("$.name", is(updatedNoteType.getName())))
      .andExpect(jsonPath("$.metadata.updatedByUserId").value(USER_ID))
      .andExpect(jsonPath("$.metadata.createdDate").isNotEmpty());
  }

  @Test
  @DisplayName("Return 422 on put note-type with duplicate name")
  void return422OnPutWithDuplicateName() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("DuplicateName");
    NoteTypeEntity noteTypeToUpdate = createNoteType("AnotherName");

    NoteType duplicateNoteType = new NoteType().name(existNoteType.getName());
    mockMvc.perform(putById(noteTypeToUpdate.getId(), duplicateNoteType))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(exceptionMatch(DataIntegrityViolationException.class))
      .andExpect(errorMessageMatch(containsString("Key (name)=(DuplicateName) already exists")));
  }

  @Test
  @DisplayName("Return 404 on update note-type by ID when it is not exist")
  void return404OnPutByIdWhenItNotExist() throws Exception {
    mockMvc.perform(putById(UUID.randomUUID(), new NoteType().name("NotExist")))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteTypeNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  // Tests for DELETE

  @Test
  @DisplayName("Delete existing note-type")
  void deleteExistingNoteType() throws Exception {
    NoteTypeEntity existNoteType = createNoteType("ToDelete");

    mockMvc.perform(deleteById(existNoteType.getId()))
      .andExpect(status().isNoContent());

    var rowsInTable = databaseHelper.countRowsInTable(TENANT, TYPE);
    assertEquals(0, rowsInTable);
  }

  @Test
  @DisplayName("Return 404 on delete note-type by ID when it is not exist")
  void return404OnDeleteByIdWhenItNotExist() throws Exception {
    mockMvc.perform(deleteById(UUID.randomUUID()))
      .andExpect(status().isNotFound())
      .andExpect(exceptionMatch(NoteTypeNotFoundException.class))
      .andExpect(errorMessageMatch(containsString("was not found")));
  }

  private NoteTypeEntity createNoteType(String name) {
    NoteTypeEntity noteType = new NoteTypeEntity();
    noteType.setId(UUID.randomUUID());
    noteType.setName(name);

    databaseHelper.saveNoteType(noteType, TENANT);
    return noteType;
  }

  private List<NoteTypeEntity> createListOfNoteTypes() {
    List<NoteTypeEntity> noteTypes = List.of(
      new NoteTypeEntity("first"),
      new NoteTypeEntity("second"),
      new NoteTypeEntity("third")
    );
    databaseHelper.saveNoteTypes(noteTypes, TENANT);
    return noteTypes;
  }

  private MockHttpServletRequestBuilder postNoteType(NoteType noteType) {
    return post(BASE_URL)
      .headers(defaultHeaders())
      .content(asJsonString(noteType));
  }

  private MockHttpServletRequestBuilder getById(Object id) {
    return get(BASE_URL + "/{id}", id)
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder putById(UUID id, NoteType noteType) {
    return put(BASE_URL + "/{id}", id)
      .content(asJsonString(noteType))
      .headers(defaultHeaders());
  }

  private MockHttpServletRequestBuilder deleteById(UUID id) {
    return delete(BASE_URL + "/{id}", id)
      .headers(defaultHeaders());
  }

  void generateNoteType(List<Note> notes) throws Exception {
    var noteType = new NoteType().name(RandomStringUtils.randomAlphabetic(100));
    var contentAsString = mockMvc.perform(postNoteType(noteType)).andReturn().getResponse().getContentAsString();
    var existingNoteType = OBJECT_MAPPER.readValue(contentAsString, NoteType.class);
    notes.forEach(note ->
    {
      note.setDomain("Domain");
      note.setTypeId(existingNoteType.getId());
      try {
        mockMvc.perform(postNote(note))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.title", is(note.getTitle())));
      } catch (Exception ignored) {
      }
    });
  }

  private MockHttpServletRequestBuilder postNote(Note note) {
    return post(NOTE_URL)
      .headers(defaultHeaders())
      .content(asJsonString(note));
  }

  private ResultMatcher errorMessageMatch(Matcher<String> errorMessageMatcher) {
    return jsonPath("$.errors.[0].message", errorMessageMatcher);
  }

  private <T> ResultMatcher exceptionMatch(Class<T> type) {
    return result -> assertThat(result.getResolvedException(), instanceOf(type));
  }
}

