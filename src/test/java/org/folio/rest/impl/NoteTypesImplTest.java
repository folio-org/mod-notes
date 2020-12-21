package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jeasy.random.FieldPredicates.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.folio.test.util.DBTestUtil.deleteFromTable;
import static org.folio.test.util.DBTestUtil.getAll;
import static org.folio.test.util.DBTestUtil.save;
import static org.folio.test.util.TestUtil.mockGet;
import static org.folio.test.util.TestUtil.readFile;
import static org.folio.test.util.TestUtil.readJsonFile;
import static org.folio.test.util.TestUtil.toJson;
import static org.folio.util.NoteTestData.NOTE_2;
import static org.folio.util.NoteTestData.NOTE_4;
import static org.folio.util.NoteTestData.USER8;
import static org.folio.util.NoteTestData.USER9;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.NotesTestBase;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.spring.SpringContextUtil;

@RunWith(VertxUnitRunner.class)
public class NoteTypesImplTest extends NotesTestBase {

  private static final int NOTE_TOTAL = 10;
  private static final String STUB_NOTE_TYPE_ID = "13f21797-d25b-46dc-8427-1759d1db2057";
  private static final String NOT_EXISTING_STUB_ID = "9798274e-ce9d-46ab-aa28-00ca9cf4698a";

  private static final String NOTE_TYPES_ENDPOINT = "/note-types";
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String NOTE_TYPES = "noteTypes";
  private static final String COLLECTION_NOTE_TYPE_JSON = "post_collection_note_type.json";
  private static final int MAX_LIMIT_AND_OFFSET = 2147483647;
  private static final int NULL_LIMIT_AND_OFFSET = 0;

  private static final RegexPattern CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN =
    //new RegexPattern("/configurations/entries.+NOTES.+note\\.types\\.number\\.limit.*"), true);
    new RegexPattern("/configurations/entries.*");

  private ObjectMapper mapper;
  private EasyRandom noteTypeRandom;
  private EasyRandom noteRandom;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    SpringContextUtil.autowireDependenciesFromFirstContext(this, vertx);

    // configure random object generator for NoteType
    EasyRandomParameters params = new EasyRandomParameters()
      .randomize(named("id"), randomUUID())
      .excludeField(named("usage"))
      .excludeField(named("metadata"));

    noteTypeRandom = new EasyRandom(params);

    // configure random object generator for Note
    params = new EasyRandomParameters()
      .randomize(named("id"), randomUUID())
      .randomize(named("typeId"), randomUUID())
      .randomize(named("title"), new StringRandomizer(75))
      .excludeField(named("creator"))
      .excludeField(named("updater"))
      .excludeField(named("metadata"));

    noteRandom = new EasyRandom(params);

    mapper = new ObjectMapper();

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/99999999-9999-4999-9999-999999999999"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_user.json"))
        ));

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/88888888-8888-4888-8888-888888888888"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_another_user.json"))
        ));

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/33999999-9999-4999-9999-999999999933"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_user_no_name.json"))
        ));

    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, HttpStatus.SC_NOT_FOUND); // default limit will be applied

    deleteFromTable(vertx, NOTE_TYPE_TABLE);
  }

  private Randomizer<String> randomUUID() {
    return () -> UUID.randomUUID().toString();
  }

  @Test
  public void shouldReturn200WithNoteTypeWhenValidId() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);

      NoteType actual = getWithOk(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID).as(NoteType.class);
      assertEquals(stubNoteType.getId(), actual.getId());
      assertEquals(stubNoteType.getName(), actual.getName());
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeUsageById() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);
      postNoteWithOk(NOTE_2, USER8);
      postNoteWithOk(NOTE_4, USER8);

      getWithValidateBody(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, SC_OK)
        .body("usage.noteTotal", is(2));
    } finally {
      deleteFromTable(vertx, NOTE_TABLE);
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeUsage() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);
      postNoteWithOk(NOTE_2, USER8);
      postNoteWithOk(NOTE_4, USER8);

      getWithValidateBody(NOTE_TYPES_ENDPOINT, SC_OK)
        .body("noteTypes[0].usage.noteTotal", is(2));
    } finally {
      deleteFromTable(vertx, NOTE_TABLE);
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollection() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);

      Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithLimitAndOffsetSet() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }

      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET + "&offset=2").response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(1, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithMaxLimit() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithMaxOffset() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?offset=" + MAX_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithOffsetNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?offset=" + NULL_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithLimitNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + NULL_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn400WhenLimitInvalid() {
    try {
      getWithStatus(NOTE_TYPES_ENDPOINT + "&limit=-1", SC_BAD_REQUEST);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn400WhenOffsetInvalid() {
    try {
      getWithStatus(NOTE_TYPES_ENDPOINT + "&offset=-1", SC_BAD_REQUEST);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithThreeNoteTypeElements() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollectionAndIncompleteWay() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);

      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?quer").response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn200WithEmptyNoteTypeCollection() {
    deleteFromTable(vertx, NOTE_TYPE_TABLE);
    Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

    int totalRecords = response.path(TOTAL_RECORDS);
    List<NoteType> noteTypes = response.path(NOTE_TYPES);

    assertEquals(0, noteTypes.size());
    assertEquals(0, totalRecords);
  }

  @Test
  public void shouldReturn400InvalidRequest() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);

      getWithStatus(NOTE_TYPES_ENDPOINT + "?query=", SC_BAD_REQUEST);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn400InvalidLimit() throws IOException, URISyntaxException {
    try {
      final NoteType stubNoteType = readJsonFile("post_note_type.json", NoteType.class);

      save(STUB_NOTE_TYPE_ID, stubNoteType, vertx, NOTE_TYPE_TABLE);

      getWithStatus(NOTE_TYPES_ENDPOINT + "?limit=", SC_BAD_REQUEST);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn404WhenInvalidNotExistingId() {
    final String response = getWithStatus(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID, SC_NOT_FOUND).asString();
    assertThat(response, containsString("not found"));
  }

  @Test
  public void shouldReturn400WhenInvalidId() {
    final String invalidStubId = "11111111-222-1111-2-111111111111";
    getWithStatus(NOTE_TYPES_ENDPOINT + "/" + invalidStubId, SC_BAD_REQUEST).asString();
  }


  @Test
  public void shouldUpdateNoteNameTypeOnPut() throws IOException, URISyntaxException {
    try {
      postNoteTypeWithOk(readFile("post_note_type.json"), USER9);
      NoteType updatedNoteType = mapper.readValue(readFile("put_note_type.json"), NoteType.class);

      putWithNoContent(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, mapper.writeValueAsString(updatedNoteType), USER8);

      NoteType loaded = loadSingleNoteType();
      assertEquals(updatedNoteType.getName(), loaded.getName());

      final Metadata noteTypeMetadata = loaded.getMetadata();
      assertEquals("99999999-9999-4999-9999-999999999999", noteTypeMetadata.getCreatedByUserId());
      assertEquals("mockuser9", noteTypeMetadata.getCreatedByUsername());

      assertEquals("88888888-8888-4888-8888-888888888888", noteTypeMetadata.getUpdatedByUserId());
      assertEquals("m8", noteTypeMetadata.getUpdatedByUsername());

    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldNotSetNoteUsageOnPut() throws IOException, URISyntaxException {
    try {
      NoteType updatedNoteType = mapper.readValue(readFile("put_note_type.json"), NoteType.class);
      updatedNoteType.withUsage(new NoteTypeUsage().withNoteTotal(NOTE_TOTAL));

      postNoteTypeWithOk(toJson(updatedNoteType), USER8);

      putWithNoContent(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, mapper.writeValueAsString(updatedNoteType), USER8);

      NoteType loaded = loadSingleNoteType();
      assertNull(loaded.getUsage());
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn404OnPutWhenNoteNotFound() throws IOException, URISyntaxException {
    putWithStatus(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, readFile("put_note_type.json"),
      SC_NOT_FOUND, USER9);
  }

  @Test
  public void shouldReturn422OnPutWhenRequestIsInvalid() {
    putWithStatus(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, "{\"name\":null}",
      SC_UNPROCESSABLE_ENTITY, USER9);
  }

  @Test
  public void shouldCreateNewNoteTypeOnPost() {
    try {
      NoteType input = nextRandomNoteType();

      NoteType response = postNoteTypeWithOk(toJson(input), USER9).as(NoteType.class);

      assertNotNull(response);
      assertEquals(input.getId(), response.getId());
      assertEquals(input.getName(), response.getName());

      NoteType loaded = loadSingleNoteType();
      assertEquals(input.getId(), loaded.getId());
      assertEquals(input.getName(), loaded.getName());

      final Metadata noteTypeMetadata = loaded.getMetadata();
      assertEquals("mockuser9", noteTypeMetadata.getCreatedByUsername());
      assertEquals("99999999-9999-4999-9999-999999999999", noteTypeMetadata.getCreatedByUserId());
      assertTrue(Objects.nonNull(noteTypeMetadata.getCreatedDate()));
      assertTrue(Objects.isNull(noteTypeMetadata.getUpdatedByUsername()));

    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn422OnPostWhenRequestIsInvalid() {
    postWithStatus(NOTE_TYPES_ENDPOINT, "{\"name\":null}", SC_UNPROCESSABLE_ENTITY, USER9);
  }

  @Test
  public void shouldFailOnPostWith400IfTypeAlreadyExists() {
    try {
      NoteType existing = nextRandomNoteType();
      save(existing.getId(), existing, vertx, NOTE_TYPE_TABLE);

      NoteType creating = new NoteType().withName(existing.getName());
      String error = postWithStatus(NOTE_TYPES_ENDPOINT, toJson(creating), SC_BAD_REQUEST, USER9).asString();

      assertThat(error, containsString("already exists"));
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldFailOnPostWith400IfNoteTypeLimitReached() throws Exception {
    // mock response with the limit = 5
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    try {
      for (int i = 0; i < 5; i++) {
        NoteType nt = nextRandomNoteType();
        save(nt.getId(), nt, vertx, NOTE_TYPE_TABLE);
      }

      NoteType creating = nextRandomNoteType();
      String error = postWithStatus("note-types/", toJson(creating), SC_BAD_REQUEST, USER9).asString();

      assertThat(error, containsString("Maximum number of note types allowed"));
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn400WhenUserIdIsMissing() {
    NoteType input = nextRandomNoteType();
    RestAssured.given()
      .spec(givenWithUrl())
      .header(TENANT_HEADER).header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body(toJson(input))
      .post(NOTE_TYPES_ENDPOINT)
      .then()
      .log().ifValidationFails()
      .statusCode(SC_UNAUTHORIZED)
      .body(containsString("Unauthorized"));
  }

  @Test
  public void shouldReturn401WhenIncorrectTenant() {
    try {
      NoteType inputPost = nextRandomNoteType();
      postNoteTypeWithOk(toJson(inputPost), USER8);

      NoteType input = nextRandomNoteType();
      RestAssured.given()
        .spec(givenWithUrl())
        .header(INCORRECT_HEADER).header(JSON_CONTENT_TYPE_HEADER).header(USER8)
        .when()
        .body(toJson(input))
        .put(NOTE_TYPES_ENDPOINT + "/" + inputPost.getId())
        .then()
        .log().ifValidationFails()
        .statusCode(SC_UNAUTHORIZED);
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldReturn400WhenUserIsRetrievedWithoutNecessaryFields() {
    NoteType input = nextRandomNoteType();
    final Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, "33999999-9999-4999-9999-999999999933");
    final String response =
      postWithStatus(NOTE_TYPES_ENDPOINT, toJson(input), SC_UNAUTHORIZED, userWithoutPermission).asString();
    assertThat(response, containsString("Unauthorized"));
  }

  @Test
  public void shouldDeleteExistingNoteTypeById() {
    try {
      NoteType existing = nextRandomNoteType();
      save(existing.getId(), existing, vertx, NOTE_TYPE_TABLE);

      deleteWithNoContent(NOTE_TYPES_ENDPOINT + "/" + existing.getId());

      List<NoteType> noteTypes = getAll(NoteType.class, vertx, NOTE_TYPE_TABLE);
      assertEquals(0, noteTypes.size());
    } finally {
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  @Test
  public void shouldFailOnDeleteWith404WhenNoteTypeNotFound() {
    deleteWithStatus(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID, SC_NOT_FOUND);
  }

  @Test
  public void shouldFailOnDeleteWith400WhenNoteTypeIsUsed() {
    try {
      NoteType noteType = nextRandomNoteType();
      save(noteType.getId(), noteType, vertx, NOTE_TYPE_TABLE);

      Note note = nextRandomNote();
      note.setType(noteType.getName());
      note.setTypeId(noteType.getId());
      save(note.getId(), note, vertx, NOTE_TABLE);

      String response = deleteWithStatus(NOTE_TYPES_ENDPOINT + "/" + noteType.getId(), SC_BAD_REQUEST).asString();
      assertThat(response, is("Note type is assigned to a note(s) and cannot be deleted"));
    } finally {
      deleteFromTable(vertx, NOTE_TABLE);
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }

  private NoteType loadSingleNoteType() {
    List<NoteType> noteTypes = getAll(NoteType.class, vertx, NOTE_TYPE_TABLE);

    assertEquals(1, noteTypes.size());
    return noteTypes.get(0);
  }

  private NoteType nextRandomNoteType() {
    return noteTypeRandom.nextObject(NoteType.class);
  }

  private Note nextRandomNote() {
    return noteRandom.nextObject(Note.class);
  }

}
