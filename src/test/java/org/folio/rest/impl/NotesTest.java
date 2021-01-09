package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.folio.test.util.DBTestUtil.deleteFromTable;
import static org.folio.test.util.TestUtil.readFile;
import static org.folio.util.NoteTestData.DOMAIN;
import static org.folio.util.NoteTestData.NOTE_1;
import static org.folio.util.NoteTestData.NOTE_2;
import static org.folio.util.NoteTestData.NOTE_3;
import static org.folio.util.NoteTestData.NOTE_4;
import static org.folio.util.NoteTestData.NOTE_5_LONG_TITLE;
import static org.folio.util.NoteTestData.NOTE_TYPE2_ID;
import static org.folio.util.NoteTestData.NOTE_TYPE2_NAME;
import static org.folio.util.NoteTestData.NOTE_TYPE_ID;
import static org.folio.util.NoteTestData.NOTE_TYPE_NAME;
import static org.folio.util.NoteTestData.PACKAGE_ID;
import static org.folio.util.NoteTestData.PACKAGE_TYPE;
import static org.folio.util.NoteTestData.UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS;
import static org.folio.util.NoteTestData.UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID;
import static org.folio.util.NoteTestData.UPDATE_NOTE_REQUEST;
import static org.folio.util.NoteTestData.UPDATE_NOTE_REQUEST_WITH_LINKS;
import static org.folio.util.NoteTestData.USER19;
import static org.folio.util.NoteTestData.USER19_ID;
import static org.folio.util.NoteTestData.USER8;
import static org.folio.util.NoteTestData.USER8_ID;
import static org.folio.util.NoteTestData.USER9;
import static org.folio.util.NoteTestData.USER9_ID;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.NotesTestBase;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.test.util.TestBase;
import org.folio.test.util.TokenTestUtil;

/**
 * Interface test for mod-notes. Tests the API with restAssured, directly
 * against the module - without any Okapi in the picture. Since we run with an
 * embedded postgres, we always start with an empty database, and can safely
 * leave test data in it.
 */
@RunWith(VertxUnitRunner.class)
public class NotesTest extends NotesTestBase {

  private static final Logger logger = LogManager.getLogger("okapi");

  // One that is not found in the mock data
  private static final String NOT_JSON = "This is not json";
  private static final String NOTES_PATH = "/notes";

  private ObjectMapper mapper;


  @BeforeClass
  public static void setUpClass(TestContext context) {
    TestBase.setUpClass(context);
    createNoteTypes(context);
  }

  @AfterClass
  public static void tearDownClass(TestContext context) {
    deleteFromTable(vertx, NOTE_TYPE_TABLE);
    TestBase.tearDownClass(context);
  }

  @Before
  public void setUp() throws Exception {
     mapper = new ObjectMapper();

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/" + USER9_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_user.json"))
        ));

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/" + USER8_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_another_user.json"))
        ));

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/" + USER19_ID), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(404))
    );

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/33999999-9999-4999-9999-999999999933"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(readFile("users/mock_user_no_name.json"))
        ));

    stubFor(
      get(new UrlPathPattern(new EqualToPattern("/users/22999999-9999-4999-9999-999999999922"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(403))
    );

  }

  @After
  public void tearDown() {
    deleteFromTable(vertx, NOTE_TABLE);
  }

  @Test
  public void shouldReturn400WhenTenantIsMissing() {
    RestAssured.given()
      .spec(givenWithUrl())
      .when()
      .get(NOTES_PATH)
      .then()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST)
      .body(containsString("Tenant"));
  }

  @Test
  public void shouldReturnEmptyListOfNotesByDefault() {

    final String response = getWithOk(NOTES_PATH).asString();
    assertThat(response, containsString("\"notes\" : [ ]"));
  }

  @Test
  public void shouldReturn400WhenContentTypeHeaderIsMissing() {
    RestAssured.given()
      .spec(givenWithUrl())
      .header(TENANT_HEADER) // no content-type header
      .body(NOT_JSON)
      .when()
      .post(NOTES_PATH)
      .then()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST)
      .body(containsString("Content-type"));
  }

  @Test
  public void shouldReturn400WhenJsonIsInvalid() {
    RestAssured.given()
      .spec(givenWithUrl())
      .header(TENANT_HEADER).header(JSON_CONTENT_TYPE_HEADER)
      .body(NOT_JSON)
      .when()
      .post(NOTES_PATH)
      .then()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST)
      .body(containsString("Json content error"));
  }

  @Test
  public void shouldReturn422WhenRequestHasTypeIdSetToNull() {
    String badJson = NOTE_1.replaceFirst("typeId", "type")
      .replaceFirst(NOTE_TYPE_ID, "ASSIGNED");

    final Errors errors = postWithStatus(NOTES_PATH, badJson, SC_UNPROCESSABLE_ENTITY, USER9).as(Errors.class);
    final Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), is("must not be null"));
    assertThat(error.getParameters().get(0).getKey(), is("typeId"));
  }

  @Test
  public void shouldReturn422WhenRequestHasUnrecognizedField() {
    String badfieldDoc = NOTE_1.replaceFirst("type", "UnknownFieldName");
    final String response = postWithStatus(NOTES_PATH, badfieldDoc, SC_UNPROCESSABLE_ENTITY, USER9).asString();
    assertThat(response, containsString("Unrecognized field"));
  }

  @Test
  public void shouldReturn400WhenUserDoesntExist() {
    // Post by an unknown user 19, lookup fails
    postWithStatus(NOTES_PATH, NOTE_1, SC_BAD_REQUEST, USER19);
  }

  @Test
  public void shouldReturn422WhenPostHasInvalidUUID() {
    String bad4 = NOTE_1.replaceAll("-1111-", "-2-");  // make bad UUID
    Errors errors = postWithStatus(NOTES_PATH, bad4, SC_UNPROCESSABLE_ENTITY, USER9).as(Errors.class);
    assertEquals("id", errors.getErrors().get(0).getParameters().get(0).getKey());
  }

  @Test
  public void shouldPostNoteSuccessfully() {
    // Post a good note
    postNoteWithOk(NOTE_1, USER9);
    final Note note = getWithOk("/notes/11111111-1111-1111-a111-111111111111").as(Note.class);
    final Metadata noteMetadata = note.getMetadata();

    assertEquals("mockuser9", noteMetadata.getCreatedByUsername());
    assertEquals("99999999-9999-4999-9999-999999999999", noteMetadata.getCreatedByUserId());
    assertTrue(Objects.nonNull(noteMetadata.getCreatedDate()));
    assertTrue(Objects.isNull(noteMetadata.getUpdatedByUsername()));
  }

  @Test
  public void shouldFindNoteAfterPost() {
    postNoteWithOk(NOTE_1, USER9);

    NoteCollection notes = getWithOk(NOTES_PATH).as(NoteCollection.class);
    Note note = notes.getNotes().get(0);

    assertThat(note.getMetadata().getCreatedByUserId(), containsString("-9999-"));
    assertEquals(NOTE_TYPE_NAME, note.getType());
    assertEquals(NOTE_TYPE_ID, note.getTypeId());
    assertEquals(DOMAIN, note.getDomain());
    assertEquals("test note title", note.getTitle());
    assertThat(note.getContent(), containsString("First note"));
    assertEquals("Mockerson", note.getCreator().getLastName());
    assertEquals("Mockey", note.getCreator().getFirstName());
    assertEquals("M.", note.getCreator().getMiddleName());
    assertEquals(1, (int) notes.getTotalRecords());

    assertThat(note.getLinks(), hasItem(allOf(
      hasProperty("id", is(PACKAGE_ID)),
      hasProperty("type", is(PACKAGE_TYPE))
    )));
  }

  @Test
  public void shouldFindNoteByIdAfterPost() {
    postNoteWithOk(NOTE_1, USER9);
    final String response = getWithOk("/notes/11111111-1111-1111-a111-111111111111").asString();
    assertThat(response, containsString("First note"));
  }

  @Test
  public void shouldFindNoteByIdWithType() {
    postNoteWithOk(NOTE_1, USER9);
    final Note note = getWithOk("/notes/11111111-1111-1111-a111-111111111111").as(Note.class);

    assertEquals(NOTE_TYPE_ID, note.getTypeId());
    assertEquals(NOTE_TYPE_NAME, note.getType());
  }

  @Test
  public void shouldGetNoteListWithTypes() {
    postNoteWithOk(NOTE_1, USER9);
    postNoteWithOk(NOTE_2, USER8);

    NoteCollection notes = getWithOk(NOTES_PATH).as(NoteCollection.class);

    assertThat(notes.getNotes(), hasItem(allOf(
      hasProperty("typeId", is(NOTE_TYPE_ID)),
      hasProperty("type", is(NOTE_TYPE_NAME))
    )));
    assertThat(notes.getNotes(), hasItem(allOf(
      hasProperty("typeId", is(NOTE_TYPE2_ID)),
      hasProperty("type", is(NOTE_TYPE2_NAME)
      ))));
  }

  @Test
  public void shouldFindSecondNoteByIdAfterPost() {
    postNoteWithOk(NOTE_2, USER8);

    final Note note = getWithOk("/notes/22222222-2222-2222-a222-222222222222").as(Note.class);
    assertThat(note.getContent(), containsString("things"));
    assertThat(note.getDomain(), equalTo("eholdings"));
    final Metadata noteMetadata = note.getMetadata();
    assertThat(noteMetadata.getCreatedByUserId(), containsString("-8888-"));
    assertThat(noteMetadata.getCreatedByUsername(), equalTo("m8"));
    assertThat(note.getCreator().getLastName(), equalTo("M8"));
  }

  @Test
  public void shouldReturn404WhenNoteIsNotFound() {
    final String response = getWithStatus("/notes/99111111-1111-1111-a111-111111111199", SC_NOT_FOUND).asString();
    assertThat(response, containsString("not found"));
  }

  @Test
  public void shouldReturn400WhenUUIDNotValid() {
    getWithStatus("/notes/777", SC_BAD_REQUEST);
  }

  @Test
  public void shouldFindNoteByContent() {
    postNoteWithOk(NOTE_1, USER9);
    getNoteAndCheckContent("?query=content=fiRST", "First note");
  }

  @Test
  public void shouldFindNoteByEntityId() {
    postNoteWithOk(NOTE_1, USER9);
    getNoteAndCheckContent("?query=linkIds=" + PACKAGE_ID, "First note");
  }

  @Test
  public void shouldFindNoteByLinkType() {
    postNoteWithOk(NOTE_1, USER9);
    getNoteAndCheckContent("?query=linkTypes=" + PACKAGE_TYPE, "First note");
  }

  @Test
  public void shouldFindNoteByLinkDomain() {
    postNoteWithOk(NOTE_1, USER9);
    getNoteAndCheckContent("?query=domain=" + DOMAIN, "First note");
  }

  @Test
  public void shouldFindNoteByNoteType() {
    postNoteWithOk(NOTE_1, USER9);
    getNoteAndCheckContent("?query=type=" + NOTE_TYPE_NAME, "First note");
  }

  @Test
  public void shouldFindNoteByPartialUserId() {
    postNoteWithOk(NOTE_1, USER9);

    final String response = getWithOk("/notes?query=metadata.createdByUserId=9999*").asString();
    assertThat(response, containsString("First note"));
  }

  @Test
  public void shouldFindNoteByUserId() {
    postNoteWithOk(NOTE_1, USER9);
    final String response = getWithOk(
      "/notes?query=metadata.createdByUserId=\"99999999-9999-4999-9999-999999999999\"").asString();
    assertThat(response, containsString("First note"));
  }

  @Test
  public void shouldFindMultipleNotes() {
    postNoteWithOk(NOTE_1, USER9);
    postNoteWithOk(NOTE_2, USER9);

    NoteCollection notes = getWithOk(
      "/notes?query=metadata.createdByUserId=\"99999999-9999-4999-9999-999999999999\"")
      .as(NoteCollection.class);

    assertEquals(2, notes.getNotes().size());
  }

  @Test
  public void shouldReturn400OnInvalidCQLQuery() {
    final String response = getWithStatus("/notes?query=VERYBADQUERY", SC_BAD_REQUEST).asString();
    assertThat(response, equalTo("Invalid query"));
  }

  @Test
  public void shouldReturn400OnEmptyCQLQuery() {
    final String response = getWithStatus("/notes?query=", SC_BAD_REQUEST).asString();
    assertThat(response, equalTo("Invalid query"));
  }

  @Test
  public void shouldReturn400WhenUserIdIsMissing() {
    RestAssured.given()
      .spec(givenWithUrl())
      .header(TENANT_HEADER).header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body(NOTE_2)
      .post(NOTES_PATH)
      .then()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST)
      .body(containsString("Unauthorized"));
  }

  @Test
  public void shouldReturn400WhenIncorrectTenant() {
    postNoteWithOk(NOTE_2, USER8);

    RestAssured.given()
      .spec(givenWithUrl())
      .header(INCORRECT_HEADER).header(JSON_CONTENT_TYPE_HEADER)
      .when()
      .body(NOTE_2)
      .put(NOTES_PATH + "/22222222-2222-2222-a222-222222222222")
      .then()
      .log().ifValidationFails()
      .statusCode(SC_BAD_REQUEST);

    deleteFromTable(vertx, NOTE_TABLE);
  }

  @Test
  public void shouldReturn400WhenUserLookupFails() {

    final String response = postWithStatus(NOTES_PATH, NOTE_2, SC_BAD_REQUEST, USER19).asString();
    assertThat(response, containsString("User not found"));
  }

  @Test
  public void shouldReturn400WhenUserLookupFailsWithAuthorizationError() {
    // Simulate permission problem in user lookup
    final Header userWithoutPermission = new Header(XOkapiHeaders.USER_ID, "22999999-9999-4999-9999-999999999922");
    postWithStatus(NOTES_PATH, NOTE_2, SC_BAD_REQUEST, userWithoutPermission);
  }

  @Test
  public void shouldReturn400WhenUserIsRetrievedWithoutNecessaryFields() {
    final Header userWithoutPermission =
      TokenTestUtil.createTokenHeader("name", "33999999-9999-4999-9999-999999999933");
    final String response = postWithStatus(NOTES_PATH, NOTE_2, SC_BAD_REQUEST, userWithoutPermission).asString();
    assertThat(response, containsString("Missing fields"));
  }

  @Test
  public void shouldReturn422WhenPostHasIdThatAlreadyExists() {
    postNoteWithOk(NOTE_2, USER8);
    // Post the same id again
    final String response = postWithStatus(NOTES_PATH, NOTE_2, SC_UNPROCESSABLE_ENTITY, USER8).asString();
    assertThat(response, containsString("violates unique constraint"));
  }

  @Test
  public void shouldReturn422WhenPostHasNoLinks() {
    postWithStatus(NOTES_PATH, UPDATE_NOTE_REQUEST, SC_UNPROCESSABLE_ENTITY, USER8);
  }

  @Test
  public void shouldReturn422WhenPostNoteTitleIsTooLong() {
    postWithStatus(NOTES_PATH, NOTE_5_LONG_TITLE, SC_UNPROCESSABLE_ENTITY, USER8);
  }

  @Test
  public void shouldReturn422WhenUpdatingNoteWithNotMatchingId() {
    final String response = putWithStatus("/notes/22222222-2222-2222-a222-222222222222",
      UPDATE_NOTE_REQUEST, SC_UNPROCESSABLE_ENTITY, USER8).asString();

    assertThat(response, containsString("Can not change Id"));
  }

  @Test
  public void shouldReturn422WhenUpdatingNoteWithInvalidId() {
    putWithStatus("/notes/21111111-1111-1111-a111-111111111111", UPDATE_NOTE_REQUEST, SC_UNPROCESSABLE_ENTITY, USER8);
  }

  @Test
  public void shouldReturn404WhenUpdatingNoteThatDoesntExist() {
    putWithStatus("/notes/33333333-3333-3333-a333-333333333333",
      UPDATE_NOTE_REQUEST.replaceAll("1", "3"), SC_NOT_FOUND, USER8);
  }

  @Test
  public void shouldUpdateNote() {
    postNoteWithOk(NOTE_1, USER8);

    final String resourcePath = "/notes/11111111-1111-1111-a111-111111111111";
    putWithNoContent(resourcePath, UPDATE_NOTE_REQUEST_WITH_LINKS, USER8);

    final Note note = getWithOk(resourcePath).as(Note.class);
    assertThat(note.getContent(), containsString("with a comment"));

    final Metadata noteMetadata = note.getMetadata();
    assertThat(noteMetadata.getUpdatedByUserId(), containsString("-8888-"));
    // The creator fields should be there, once we mark them read-only, and
    // the RMB keeps such in place. MODNOTES-31
  }

  @Test
  public void shouldUpdateNoteWhenPutRequestIsSameAsGetResponse() {
    postNoteWithOk(NOTE_2, USER8);
    // Update note, by fetching and PUTting back
    final String resourcePath = "/notes/22222222-2222-2222-a222-222222222222";
    String rawNote2 = getWithOk(resourcePath).asString();

    String newNote2 = rawNote2
      .replaceAll("8888", "9999") // createdBy
      .replaceFirst("23456", "34567") // link to the thing
      .replaceAll("things", "rooms") // new domain
      .replaceFirst("\"m8\"", "\"NewCrUsername\""); // readonly field, should not matter

    logger.info("About to PUT note: " + newNote2);

    putWithNoContent(resourcePath, newNote2, USER9);
  }

  @Test
  public void shouldDeleteNoteWhenPutRequestHasNoLinks() {

    postNoteWithOk(NOTE_4, USER8);
    final String resourcePath = "/notes/33333333-1111-1111-a333-333333333333";
    putWithStatus(resourcePath, UPDATE_NOTE_2_REQUEST_WITH_NO_LINKS, SC_NO_CONTENT, USER8);

    final String response = getWithStatus(resourcePath, SC_NOT_FOUND).asString();
    assertThat(response, containsString("not found"));
  }

  @Test
  public void shouldReturn422WhenNonExistingTypeIdInPutRequest() {
    postNoteWithOk(NOTE_4, USER8);
    putWithStatus("/notes/33333333-1111-1111-a333-333333333333", UPDATE_NOTE_5_REQUEST_WITH_NON_EXISTING_TYPE_ID,
      SC_UNPROCESSABLE_ENTITY, USER8);
  }

  @Test
  public void shouldReturn400WhenDeleteRequestHasInvalidUUID() {
    deleteWithStatus("/notes/11111111-3-1111-333-111111111111", SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturn404WhenDeleteRequestHasNotExistingUUID() {
    deleteWithStatus("/notes/11111111-2222-3333-a444-555555555555", SC_NOT_FOUND);
  }

  @Test
  public void shouldReturn422WhenPutNoteTitleIsTooLong() {
    postNoteWithOk(NOTE_4, USER8);
    putWithStatus("/notes/33333333-1111-1111-a333-333333333333", NOTE_5_LONG_TITLE, SC_UNPROCESSABLE_ENTITY, USER8);
  }

  @Test
  public void shouldDeleteNote() {
    postNoteWithOk(NOTE_1, USER9);

    final String resourcePath = "/notes/11111111-1111-1111-a111-111111111111";
    deleteWithNoContent(resourcePath);
    deleteWithStatus(resourcePath, SC_NOT_FOUND);

    final String response = getWithOk(NOTES_PATH).asString();
    assertThat(response, containsString("\"notes\" : [ ]"));
  }

  @Test
  public void shouldGenerateUuidInPostRequestIfItIsNotSet() {
    postNoteWithOk(NOTE_3, USER9);
    final NoteCollection noteCollection = getWithOk(NOTES_PATH).as(NoteCollection.class);
    assertThat(noteCollection.getTotalRecords(), equalTo(1));

    final Note note = noteCollection.getNotes().get(0);
    assertTrue(Objects.nonNull(note.getId()));
    assertThat(note.getContent(), containsString("no id"));

    final Metadata noteMetadata = note.getMetadata();
    assertThat(noteMetadata.getCreatedByUsername(), equalTo("mockuser9"));
    assertThat(noteMetadata.getCreatedByUserId(), containsString("-9999-"));
  }

  @Test
  public void shouldGetNotesWhenLimitIs1001() {
    postNoteWithOk(NOTE_3, USER9);

    final NoteCollection noteCollection = getWithOk("/notes?query=title=testing&limit=1001").as(NoteCollection.class);
    assertThat(noteCollection.getTotalRecords(), is(1));

    final Note note = noteCollection.getNotes().get(0);
    assertThat(note.getContent(), containsString("no id"));
    assertTrue(Objects.nonNull(note.getId()));

    final Metadata noteMetadata = note.getMetadata();
    assertThat(noteMetadata.getCreatedByUserId(), containsString("-9999-"));
  }

  @Test
  public void shouldReturn400WhenOffsetIsInvalid() {
    getWithStatus("/notes?query=title=testings&offset=-1", SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturn400WhenLimitIsInvalid() {
    getWithStatus("/notes?query=title=testing&limit=-1", SC_BAD_REQUEST);
  }

  @Test
  public void shouldDeleteNoteByPathReturnedFromPost() {
    final String location = postNoteWithOk(NOTE_3, USER9).headers().get("Location").getValue();
    deleteWithNoContent(location);
  }

  @Test
  public void shouldSaveNoteWithArbitraryAttribute() throws IOException {
    Note note = mapper.readValue(NOTE_1, Note.class);

    String name = RandomStringUtils.randomAlphabetic(10);
    String value = RandomStringUtils.randomAlphanumeric(32);
    note.setAdditionalProperty(name, value);

    postNoteWithOk(mapper.writeValueAsString(note), USER9);

    final Note saved = getWithOk("/notes/11111111-1111-1111-a111-111111111111").as(Note.class);

    assertThat(saved.getAdditionalProperties().size(), is(1));
    assertThat(saved.getAdditionalProperties(), hasEntry(name, value));
  }

  private void getNoteAndCheckContent(String query, String content) {
    final String response = getWithOk(NOTES_PATH + query).asString();
    assertThat(response, containsString(content));
  }

}
