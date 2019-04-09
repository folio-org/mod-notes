package org.folio.rest.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HTTP;
import org.folio.rest.TestBase;
import org.folio.rest.jaxrs.model.NoteType;
import org.folio.rest.jaxrs.model.NoteTypeUsage;
import org.folio.rest.persist.PostgresClient;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.folio.rest.impl.TestUtil.readFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

  @Test
  public void shouldReturn200WithNoteTypeWhenValidId() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

      RestAssured.given()
        .spec(getRequestSpecification())
        .when()
        .get(NOTE_TYPES_ENDPOINT + "/" + STUB_NOTE_TYPE_ID)
        .then()
        .statusCode(200).extract().asString();
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn200WithNoteTypeCollection() throws IOException, URISyntaxException {
    try {
      final String stubNoteType = readFile("post_note_type.json");

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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
        DBTestUtil.insertNoteType(vertx, noteType.getId(), STUB_TENANT, mapper.writeValueAsString(noteType));
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

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

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

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

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

      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, stubNoteType);

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

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID)
      .then()
      .statusCode(404).extract().asString();
  }

  @Test
  public void shouldReturn500WhenErrorOccurred() {

    final String invalidStubId = "11111111-222-1111-2-111111111111";
    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT + "/" + invalidStubId), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(500)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(NOTE_TYPES_ENDPOINT + "/" + invalidStubId)
      .then()
      .statusCode(500).extract().asString();
  }

  @Test
  public void shouldReturn501WhenDeleteEndpointNotImplemented() {

    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(501)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .delete(NOTE_TYPES_ENDPOINT + "/" + NOT_EXISTING_STUB_ID)
      .then()
      .statusCode(501).extract().asString();
  }

  @Test
  public void shouldReturn501WhenPostEndpointNotImplemented() throws IOException, URISyntaxException {

    stubFor(
      get(new UrlPathPattern(new EqualToPattern(NOTE_TYPES_ENDPOINT), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(501)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .header(CONTENT_TYPE_HEADER)
      .body(readFile("post_note_type.json"))
      .when()
      .post(NOTE_TYPES_ENDPOINT)
      .then()
      .statusCode(501).extract().asString();
  }

  @Test
  public void shouldUpdateNoteNameTypeOnPut() throws IOException, URISyntaxException {
    try {
      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, TestUtil.readFile("post_note.json"));
      NoteType updatedNoteType = mapper.readValue(TestUtil.readFile("put_note.json"), NoteType.class);
      updateNoteType(updatedNoteType);

      List<NoteType> noteTypes = DBTestUtil.getAllNoteTypes(vertx);
      assertEquals(1, noteTypes.size());
      assertEquals(updatedNoteType.getName(), noteTypes.get(0).getName());
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldNotSetNoteUsageOnPut() throws IOException, URISyntaxException {
    try {
      DBTestUtil.insertNoteType(vertx, STUB_NOTE_TYPE_ID, STUB_TENANT, TestUtil.readFile("post_note.json"));
      NoteType updatedNoteType = mapper.readValue(TestUtil.readFile("put_note.json"), NoteType.class);
      updatedNoteType.withUsage(new NoteTypeUsage().withNoteTotal(NOTE_TOTAL));
      updateNoteType(updatedNoteType);

      List<NoteType> noteTypes = DBTestUtil.getAllNoteTypes(vertx);
      assertEquals(1, noteTypes.size());
      assertNull(noteTypes.get(0).getUsage());
    } finally {
      DBTestUtil.deleteFromTable(vertx, (PostgresClient.convertToPsqlStandard(STUB_TENANT) + "." + DBTestUtil.NOTE_TYPE_TABLE));
    }
  }

  @Test
  public void shouldReturn404OnPutWhenNoteNotFound() throws IOException, URISyntaxException {
    RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body(TestUtil.readFile("put_note.json"))
      .put("note-types/" + STUB_NOTE_TYPE_ID)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturn400OnPutWhenRequestIsInvalid() {
    RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body("{\"name\":null}")
      .put("note-types/" + STUB_NOTE_TYPE_ID)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  private void updateNoteType(NoteType updatedNoteType) throws JsonProcessingException {
    RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body(mapper.writeValueAsString(updatedNoteType))
      .put("note-types/" + STUB_NOTE_TYPE_ID)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }
}
