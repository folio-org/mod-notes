package org.folio.rest.impl;

import static org.hamcrest.Matchers.containsString;
import static org.jeasy.random.FieldPredicates.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import static org.folio.util.TestUtil.readFile;
import static org.folio.util.TestUtil.toJson;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HTTP;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.persist.PostgresClient;

@RunWith(VertxUnitRunner.class)
public class NoteTypesImplTest extends TestBase {

  private static final int NOTE_TOTAL = 10;
  private static final String STUB_NOTE_TYPE_ID = "2cf21797-d25b-46dc-8427-1759d1db2057";
  private static final String NOT_EXISTING_STUB_ID = "9798274e-ce9d-46ab-aa28-00ca9cf4698a";
  private static final String NOTE_TYPES_ENDPOINT = "/note-types";
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String NOTE_TYPES = "noteTypes";
  private static final String COLLECTION_NOTE_TYPE_JSON = "post_collection_note_type.json";
  private static final int MAX_LIMIT_AND_OFFSET = 2147483647;
  private static final int NULL_LIMIT_AND_OFFSET = 0;
  private static final Header CONTENT_TYPE_HEADER = new Header(HTTP.CONTENT_TYPE, "application/json");

  private ObjectMapper mapper = new ObjectMapper();
  private EasyRandom noteTypeRandom;


  public NoteTypesImplTest() {
    // configure random object generator for NoteType
    EasyRandomParameters params = new EasyRandomParameters()
      .randomize(named("id"), () -> UUID.randomUUID().toString())
      .excludeField(named("usage"))
      .excludeField(named("metadata"));

    noteTypeRandom = new EasyRandom(params);
  }

  @Test
  public void shouldReturn200WithNoteTypeWhenValidId() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, stubNoteType);

      getWithOk(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID).asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollection() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, stubNoteType);

      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithLimitAndOffsetSet() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET + "&offset=2")
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(1, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithMaxLimit() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?limit=" + MAX_LIMIT_AND_OFFSET)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithMaxOffset() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?offset=" + MAX_LIMIT_AND_OFFSET)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithOffsetNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?offset=" + NULL_LIMIT_AND_OFFSET)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithLimitNull() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?limit=" + NULL_LIMIT_AND_OFFSET)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(0, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn400WhenLimitInvalid() {
    try {
      RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "&limit=-1")
        .then()
        .statusCode(400)
        .extract().asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn400WhenOffsetInvalid() {
    try {
      RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "&offset=-1")
        .then()
        .statusCode(400)
        .extract().asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithThreeNoteTypeElements() throws IOException, URISyntaxException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      NoteType[] noteTypes = mapper.readValue(readFile(COLLECTION_NOTE_TYPE_JSON), NoteType[].class);

      for (NoteType noteType : noteTypes) {
        DBTestUtil.insertNoteType(vertx, noteType.getId(), mapper.writeValueAsString(noteType));
      }
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypeList = response.path(NOTE_TYPES);

      assertEquals(3, noteTypeList.size());
      assertEquals(3, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollectionAndIncompleteWay() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, stubNoteType);

      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?quer")
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(1, noteTypes.size());
      assertEquals(1, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithEmptyNoteTypeCollection() {
    try {
      Response response = RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT)
        .then()
        .statusCode(200)
        .extract().response();

      int totalRecords = response.path(TOTAL_RECORDS);
      List<NoteType> noteTypes = response.path(NOTE_TYPES);

      assertEquals(0, noteTypes.size());
      assertEquals(0, totalRecords);
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn400InvalidRequest() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, stubNoteType);

      RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?query=")
        .then()
        .statusCode(400)
        .extract().asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn400InvalidLimit() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, stubNoteType);

      RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "?limit=")
        .then()
        .statusCode(400)
        .extract().asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn404WhenInvalidId() {
    getWithStatus(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID, HttpStatus.SC_NOT_FOUND).asString();
  }

  @Test
  public void shouldUpdateNoteNameTypeOnPut() throws IOException, URISyntaxException {
    try {
      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, readFile("post_note.json"));
      NoteType updatedNoteType = mapper.readValue(readFile("put_note.json"), NoteType.class);
      updateNoteType(updatedNoteType);

      NoteType loaded = loadSingleNote();
      assertEquals(updatedNoteType.getName(), loaded.getName());
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldNotSetNoteUsageOnPut() throws IOException, URISyntaxException {
    try {
      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, readFile("post_note.json"));
      NoteType updatedNoteType = mapper.readValue(readFile("put_note.json"), NoteType.class);
      updatedNoteType.withUsage(new NoteTypeUsage().withNoteTotal(NOTE_TOTAL));

      updateNoteType(updatedNoteType);

      NoteType loaded = loadSingleNote();
      assertNull(loaded.getUsage());
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn404OnPutWhenNoteNotFound() throws IOException, URISyntaxException {
    putWithStatus("note-types/" + STUB_NOTE_TYPE_ID, readFile("put_note.json"),
      HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturn400OnPutWhenRequestIsInvalid() {
    putWithStatus("note-types/" + STUB_NOTE_TYPE_ID, "{\"name\":null}",
      HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  private void updateNoteType(NoteType updatedNoteType) throws JsonProcessingException {
    putWithStatus("note-types/" + STUB_NOTE_TYPE_ID, mapper.writeValueAsString(updatedNoteType),
      HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void shouldCreateNewNoteTypeOnPost() {
    try {
      NoteType input = nextRandomNoteType();

      NoteType response = postWithStatus("note-types/", toJson(input), HttpStatus.SC_CREATED)
        .as(NoteType.class);

      assertNotNull(response);
      assertEquals(input.getId(), response.getId());
      assertEquals(input.getName(), response.getName());

      NoteType loaded = loadSingleNote();
      assertEquals(input.getId(), loaded.getId());
      assertEquals(input.getName(), loaded.getName());
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldReturn422OnPostWhenRequestIsInvalid() {
    postWithStatus("note-types/", "{\"name\":null}", HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldFailOnPostWith400IfTypeAlreadyExists() {
    try {
      NoteType existing = nextRandomNoteType();
      DBTestUtil.insertNoteType(vertx, existing.getId(), toJson(existing));

      NoteType creating = new NoteType().withName(existing.getName());
      String error = postWithStatus("note-types/", toJson(creating), HttpStatus.SC_BAD_REQUEST)
        .asString();

      assertThat(error, containsString("duplicate key value"));
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldDeleteExistingNoteTypeById() {
    try {
      NoteType existing = nextRandomNoteType();
      DBTestUtil.insertNoteType(vertx, existing.getId(), toJson(existing));

      deleteWithStatus("note-types/" + existing.getId(), HttpStatus.SC_NO_CONTENT);

      List<NoteType> noteTypes = DBTestUtil.getAllNoteTypes(vertx);
      assertEquals(0, noteTypes.size());
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }

  @Test
  public void shouldFailOnDeleteWith404WhenNoteNotFound() {
    deleteWithStatus("note-types/" + NOT_EXISTING_STUB_ID, HttpStatus.SC_NOT_FOUND);
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
