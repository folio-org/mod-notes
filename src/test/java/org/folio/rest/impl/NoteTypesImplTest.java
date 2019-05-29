package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jeasy.random.FieldPredicates.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static org.folio.test.util.TestUtil.mockGet;
import static org.folio.test.util.TestUtil.readFile;
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
import org.hamcrest.MatcherAssert;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.spring.SpringContextUtil;

@RunWith(VertxUnitRunner.class)
public class NoteTypesImplTest extends TestBase {

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


  @Before
  public void setUp() throws IOException, URISyntaxException {
    SpringContextUtil.autowireDependenciesFromFirstContext(this, vertx);

    // configure random object generator for NoteType
    EasyRandomParameters params = new EasyRandomParameters()
      .randomize(named("id"), () -> UUID.randomUUID().toString())
      .excludeField(named("usage"))
      .excludeField(named("metadata"));

    noteTypeRandom = new EasyRandom(params);

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
  }

  @Test
  public void shouldReturn200WithNoteTypeWhenValidId() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      getWithOk(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID).asString();
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeUsageById() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);
      postNoteWithOk(NOTE_2,USER8);
      postNoteWithOk(NOTE_4,USER8);

      getWithValidateBody(NOTE_TYPES_ENDPOINT +"/" + STUB_NOTE_TYPE_ID,SC_OK)
        .body("usage.noteTotal",is(2));
    } finally {
      DBTestUtil.deleteAllNotes(vertx);
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeUsage() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);
      postNoteWithOk(NOTE_2,USER8);
      postNoteWithOk(NOTE_4,USER8);

      getWithValidateBody(NOTE_TYPES_ENDPOINT,SC_OK)
        .body("noteTypes[0].usage.noteTotal",is(2));
    } finally {
      DBTestUtil.deleteAllNotes(vertx);
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollection() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithLimitAndOffsetSet() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }

      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET + "&offset=2").response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(1, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithMaxLimit() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithMaxOffset() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?offset=" + MAX_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithOffsetNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?offset=" + NULL_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithLimitNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?limit=" + NULL_LIMIT_AND_OFFSET).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn400WhenLimitInvalid() {
    try {
      getWithStatus(NOTE_TYPES_ENDPOINT + "&limit=-1", SC_BAD_REQUEST);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn400WhenOffsetInvalid() {
    try {
      getWithStatus(NOTE_TYPES_ENDPOINT + "&offset=-1", SC_BAD_REQUEST);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithThreeNoteTypeElements() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
      }
      Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollectionAndIncompleteWay() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      Response response = getWithOk(NOTE_TYPES_ENDPOINT + "?quer").response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn200WithEmptyNoteTypeCollection() {
    try {
      Response response = getWithOk(NOTE_TYPES_ENDPOINT).response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(0, noteTypes.size());
      assertEquals(0, totalRecords);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn400InvalidRequest() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      getWithStatus(NOTE_TYPES_ENDPOINT + "?query=", SC_BAD_REQUEST);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn400InvalidLimit() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      getWithStatus(NOTE_TYPES_ENDPOINT + "?limit=", SC_BAD_REQUEST);
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
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

      putWithOk(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, mapper.writeValueAsString(updatedNoteType), USER8);

      NoteType loaded = loadSingleNote();
      assertEquals(updatedNoteType.getName(), loaded.getName());

      final Metadata noteTypeMetadata = loaded.getMetadata();
      assertEquals("99999999-9999-4999-9999-999999999999", noteTypeMetadata.getCreatedByUserId());
      assertEquals("mockuser9", noteTypeMetadata.getCreatedByUsername());

      assertEquals("88888888-8888-4888-8888-888888888888", noteTypeMetadata.getUpdatedByUserId());
      assertEquals("m8", noteTypeMetadata.getUpdatedByUsername());

    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldNotSetNoteUsageOnPut() throws IOException, URISyntaxException {
    try {
      NoteType updatedNoteType = mapper.readValue(readFile("put_note_type.json"), NoteType.class);
      updatedNoteType.withUsage(new NoteTypeUsage().withNoteTotal(NOTE_TOTAL));

      postNoteTypeWithOk(toJson(updatedNoteType), USER8);

      putWithOk(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID, mapper.writeValueAsString(updatedNoteType), USER8);

      NoteType loaded = loadSingleNote();
      assertNull(loaded.getUsage());
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
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

      NoteType loaded = loadSingleNote();
      assertEquals(input.getId(), loaded.getId());
      assertEquals(input.getName(), loaded.getName());

      final Metadata noteTypeMetadata = loaded.getMetadata();
      assertEquals("mockuser9", noteTypeMetadata.getCreatedByUsername());
      assertEquals("99999999-9999-4999-9999-999999999999", noteTypeMetadata.getCreatedByUserId());
      assertTrue(Objects.nonNull(noteTypeMetadata.getCreatedDate()));
      assertTrue(Objects.isNull(noteTypeMetadata.getUpdatedByUsername()));

    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
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
      DBTestUtil.insertNoteType(vertx, existing.getId(), STUB_TENANT, toJson(existing));

      NoteType creating = new NoteType().withName(existing.getName());
      String error = postWithStatus(NOTE_TYPES_ENDPOINT, toJson(creating), SC_BAD_REQUEST, USER9).asString();

      assertThat(error, containsString("already exists"));
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldFailOnPostWith400IfNoteTypeLimitReached() throws Exception {
    // mock response with the limit = 5
    mockGet(CONFIG_NOTE_TYPE_LIMIT_URL_PATTERN, "responses/configuration/get-note-type-limit-5-response.json");

    try {
      for (int i = 0; i < 5; i++) {
        NoteType nt = nextRandomNoteType();
        DBTestUtil.insertNoteType(vertx, nt.getId(), STUB_TENANT, toJson(nt));
      }

      NoteType creating = nextRandomNoteType();
      String error = postWithStatus("note-types/", toJson(creating), SC_BAD_REQUEST, USER9).asString();

      assertThat(error, containsString("Maximum number of note types allowed"));
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
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
      .statusCode(SC_BAD_REQUEST)
      .body(containsString("cannot look up user"));
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
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn400WhenUserIsRetrievedWithoutNecessaryFields() {
    NoteType input = nextRandomNoteType();
    final Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, "33999999-9999-4999-9999-999999999933");
    final String response = postWithStatus(NOTE_TYPES_ENDPOINT,  toJson(input), SC_BAD_REQUEST, userWithoutPermission).asString();
    MatcherAssert.assertThat(response, containsString("Missing fields"));
  }

  @Test
  public void shouldDeleteExistingNoteTypeById() {
    try {
      NoteType existing = nextRandomNoteType();
      DBTestUtil.insertNoteType(vertx, existing.getId(), STUB_TENANT, toJson(existing));

      deleteWithOk(NOTE_TYPES_ENDPOINT + "/" + existing.getId());

      List<NoteType> noteTypes = DBTestUtil.getAllNoteTypes(vertx);
      assertEquals(0, noteTypes.size());
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldFailOnDeleteWith404WhenNoteNotFound() {
    deleteWithStatus(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID, SC_NOT_FOUND);
  }

  private NoteType loadSingleNote() {
    List<NoteType> noteTypes = DBTestUtil.getAllNoteTypes(vertx);

    assertEquals(1, noteTypes.size());
    return noteTypes.get(0);
  }

  private NoteType nextRandomNoteType() {
    return noteTypeRandom.nextObject(NoteType.class);
  }

}
