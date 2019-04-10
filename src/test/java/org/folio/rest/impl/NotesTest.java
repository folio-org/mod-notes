package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import static org.folio.util.TestUtil.readFile;

import java.io.IOException;
import java.util.Locale;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Ignore;

/**
 * Interface test for mod-notes. Tests the API with restAssured, directly
 * against the module - without any Okapi in the picture. Since we run with an
 * embedded postgres, we always start with an empty database, and can safely
 * leave test data in it.
 *
 * @author heikki
 */
@RunWith(VertxUnitRunner.class)
public class NotesTest {

  private static final Logger logger = LoggerFactory.getLogger("okapi");
  private static final String LS = System.lineSeparator();
  private static final String HOST = "http://127.0.0.1";
  private static final String TENANT = "modnotestest";
  private static final Header TEN = new Header("X-Okapi-Tenant", TENANT);
  private static final Header USER9 = new Header("X-Okapi-User-Id",
    "99999999-9999-4999-9999-999999999999");
  private static final Header USER19 = new Header("X-Okapi-User-Id",
    "11999999-9999-4999-9999-999999999911");  // One that is not found in the mock data
  private static final Header USER8 = new Header("X-Okapi-User-Id",
    "88888888-8888-4888-8888-888888888888");
  private static final Header USER7 = new Header("X-Okapi-User-Id",
    "77777777-7777-4777-a777-777777777777");
  private static final Header JSON = new Header("Content-Type", "application/json");
  private static final String NOT_JSON = "This is not json";

  private static final String NOTE_TYPE_ID = "2af21797-d25b-46dc-8427-1759d1db2057";
  private static final String NOTE_TYPE2_ID = "13f21797-d25b-46dc-8427-1759d1db2057";
  private static final String NOTE_TYPE_NAME = "High Priority";
  private static final String NOTE_TYPE2_NAME = "test note";
  private static final String NOTE_TYPE = "{\"id\":\""+ NOTE_TYPE_ID+ "\", \"name\":\"" + NOTE_TYPE_NAME + "\"}";
  private static final String NOTE_TYPE2 = "{\"id\":\""+ NOTE_TYPE2_ID+ "\", \"name\":\"" + NOTE_TYPE2_NAME + "\"}";

  private static final String NOTE_1 = "{"
    + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
    + "\"typeId\" : \"" + NOTE_TYPE_ID + "\"," + LS
    + "\"title\" : \"test note title\"," + LS
    + "\"content\" : \"First note email@folio.org\"}" + LS;
  private static final String NOTE_2 = "{"
    + "\"id\" : \"22222222-2222-2222-a222-222222222222\"," + LS
    + "\"typeId\" : \"" + NOTE_TYPE2_ID + "\"," + LS
    + "\"title\" : \"things\"," + LS
    + "\"content\" : \"Test on things\"}" + LS;
  private static final String UPDATE_NOTE_REQUEST = "{"
    + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
    + "\"typeId\" : \"" + NOTE_TYPE_ID + "\"," + LS
    + "\"title\" : \"more things\"," + LS
    + "\"content\" : \"First note with a comment\"}" + LS;
  private static final String NOTE_3 = "{"
    + "\"typeId\" : \"" + NOTE_TYPE_ID + "\"," + LS
    + "\"title\" : \"testing\"," + LS
    + "\"content\" : \"Note with no id\"}" + LS;
  private static String moduleName; //  "mod-notes"
  private static String moduleVersion; // "1.0.0" or "0.1.2-SNAPSHOT"
  private static String moduleId; // "mod-notes-1.0.1-SNAPSHOT"
  private static Vertx vertx;
  private static Async async;
  private static int port;
  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @BeforeClass
  public static void setUpBeforeClass(TestContext context) {
    Locale.setDefault(Locale.US);  // enforce English error messages
    vertx = Vertx.vertx();
    moduleName = PomReader.INSTANCE.getModuleName()
      .replaceAll("_", "-");  // RMB normalizes the dash to underscore, fix back
    moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = moduleName + "-" + moduleVersion;
    logger.info("Test setup starting for " + moduleId);
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (IOException e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }

    port = NetworkUtils.nextFreePort();

    JsonObject conf = new JsonObject()
      .put("http.port", port);
    logger.info("notesTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);

    RestAssured.port = port;

    async = context.async();
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, result -> {
        // Call the tenant interface to initialize the database
        String tenants = "{\"module_to\":\"" + moduleId + "\"}";
        logger.info("About to call the tenant interface " + tenants);
        given()
          .header(TEN).header(JSON)
          .body(tenants)
          .post("/_/tenant")
          .then()
          .log().ifValidationFails()
          .statusCode(201);

        // Simple GET request to see the module is running and we can talk to it.
        given()
          .get("/admin/health")
          .then()
          .log().all()
          .statusCode(200);

        vertx.executeBlocking(future -> {
            DBTestUtil.insertNoteType(vertx, NOTE_TYPE_ID, TENANT, NOTE_TYPE);
            DBTestUtil.insertNoteType(vertx, NOTE_TYPE2_ID, TENANT, NOTE_TYPE2);
            future.complete();
          },
          vertxResult -> async.complete());

      });

    logger.info("notesTest: setup done. Using port " + port);
  }

  @AfterClass
  public static void tearDownBeforeClass(TestContext context) {
    logger.info("Cleaning up after ModuleTest");
    async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
    }));
  }

  @Before
  public void setUp() throws Exception {
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
      get(new UrlPathPattern(new EqualToPattern("/users/11999999-9999-4999-9999-999999999911"), false))
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

    stubFor(
      post(new UrlPathPattern(new EqualToPattern("/notify/_username/foo"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(201))
    );

    stubFor(
      post(new UrlPathPattern(new EqualToPattern("/notify/_username/üñí"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(201))
    );

    stubFor(
      post(new UrlPathPattern(new EqualToPattern("/notify/_username/bar"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(404))
    );

    stubFor(
      post(new UrlPathPattern(new EqualToPattern("/notify/_username/broken"), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(500))
    );
  }

  @After
  public void tearDown() {
    DBTestUtil.deleteFromTable(vertx,
      (PostgresClient.convertToPsqlStandard(TENANT) + "." + DBTestUtil.NOTE_TABLE));
  }

  @Test
  public void shouldReturn400WhenTenantIsMissing() {
    givenWithUrl()
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Tenant"));
  }

  @Test
  public void shouldReturnEmptyListOfNotesByDefault(){
    givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));
  }

  @Test
  public void shouldReturn400WhenContentTypeHeaderIsMissing() {
    givenWithUrl()
      .header(TEN) // no content-type header
      .body(NOT_JSON)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Content-type"));
  }

  @Test
  public void shouldReturn400WhenJsonIsInvalid() {
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(NOT_JSON)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Json content error"));
  }

  @Test
  public void shouldReturn422WhenRequestHasTypeIdSetToNull() {
    String badJson = NOTE_1.replaceFirst("typeId", "status")
      .replaceFirst(NOTE_TYPE_ID, "ASSIGNED");
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(badJson)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      // English error message for Locale.US, see @Before
      .body("errors[0].message", is("may not be null"))
      .body("errors[0].parameters[0].key", is("typeId"));
  }

  @Test
  public void shouldReturn422WhenRequestHasUnrecognizedField() {
    String badfieldDoc = NOTE_1.replaceFirst("type", "UnknownFieldName");
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(badfieldDoc)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Unrecognized field"));
  }

  @Test
  public void shouldReturn400WhenUserDoesntExist() {
    // Post by an unknown user 19, lookup fails
    sendPostRequest(NOTE_1, USER19)
      .statusCode(400);
  }

  @Test
  public void shouldReturn422WhenPostHasInvalidUUID() {
    String bad4 = NOTE_1.replaceAll("-1111-", "-2-");  // make bad UUID
    sendPostRequest(bad4, USER9)
      .statusCode(422)
      .body(containsString("invalid input syntax for type uuid"));
  }

  @Test
  public void shouldPostNoteSuccessfully() {
    // Post a good note
    sendOkPostRequest(NOTE_1, USER9);
  }

  @Test
  public void shouldFindNoteAfterPost() {
    sendOkPostRequest(NOTE_1, USER9);

    // Fetch the note in various ways
    givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("Mockerson")) // Creator lastName
      .body(containsString("Mockey")) // Creator firstName
      .body(containsString("M.")) // Creator middleName
      .body(containsString("\"totalRecords\" : 1"));
  }

  @Test
  public void shouldFindNoteByIdAfterPost() {
    sendOkPostRequest(NOTE_1, USER9);
    givenWithUrl()
      .header(TEN)
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));
  }

  @Test
  public void shouldFindNoteByIdWithType() {
    sendOkPostRequest(NOTE_1, USER9);
    Note note = givenWithUrl()
      .header(TEN)
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract().as(Note.class);

    assertEquals(NOTE_TYPE_ID, note.getTypeId());
    assertEquals(NOTE_TYPE_NAME , note.getType());
  }

  @Test
  public void shouldGetNoteListWithTypes() {
    sendOkPostRequest(NOTE_1, USER9);
    sendOkPostRequest(NOTE_2, USER8);
    NoteCollection notes = givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract().as(NoteCollection.class);

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
    sendOkPostRequest(NOTE_2, USER8);
    givenWithUrl()
      .header(TEN)
      .get("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .statusCode(200)
      .log().ifValidationFails()
      .body(containsString("-8888-")) // metadata.createdByUserId
      .body(containsString("lastName"))
      .body(containsString("createdByUsername"))
      .body(containsString("things"));
  }

  @Test
  public void shouldReturn404WhenNoteIsNotFound() {
    givenWithUrl()
      .header(TEN)
      .get("/notes/99111111-1111-1111-a111-111111111199")
      .then()
      .log().ifValidationFails()
      .statusCode(404)
      .body(containsString("Not found"));
  }

  @Test
  public void shouldReturn500WhenUUIDNotValid() {
    givenWithUrl()
      .header(TEN)
      .get("/notes/777")
      .then()
      .log().ifValidationFails()
      .statusCode(500);
  }

  @Test
  public void shouldFindNoteByContent() {
    sendOkPostRequest(NOTE_1, USER9);
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=content=fiRST")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("id"));
  }

  @Test
  public void shouldFindNoteByPartialUserId() {
    sendOkPostRequest(NOTE_1, USER9);
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=metadata.createdByUserId=*9999*")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));
  }

  @Test
  public void shouldFindNoteByUserId() {
    sendOkPostRequest(NOTE_1, USER9);
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=metadata.createdByUserId=\"99999999-9999-4999-9999-999999999999\"")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));
  }

  @Test
  public void shouldFindMultipleNotes() {
    sendOkPostRequest(NOTE_1, USER9);
    sendOkPostRequest(NOTE_2, USER9);

    NoteCollection notes = givenWithUrl()
      .header(TEN)
      .get("/notes?query=metadata.createdByUserId=\"99999999-9999-4999-9999-999999999999\"")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract().as(NoteCollection.class);

    assertEquals(2, notes.getNotes().size());
  }

  @Test
  public void shouldReturn422OnInvalidCQLQuery() {
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=VERYBADQUERY")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("no serverChoiceIndexes defined"));
  }


  @Test
  @Ignore
  public void shouldReturn422WhenCQLQueryContainsUnknownField() {
    // Why do the next two not fail with a QueryValidationException ??
    // When run manually (run.sh), they return a 422 all right
    // See MODNOTES-15, and the comments in NotesResourceImpl.java around initCQLValidation()
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=metadata.UNKNOWNFIELD=foobar")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("is not present in index"));

    givenWithUrl()
      .header(TEN)
      .get("/notes?query=UNKNOWNFIELD=foobar")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("is not present in index"));
  }

  @Test
  public void shouldReturn400WhenUserIdIsMissing() {
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(NOTE_2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .body(containsString("Can not look up user"))
      .statusCode(400);
  }

  @Test
  public void shouldReturn400WhenUserLookupFails() {
    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "11999999-9999-4999-9999-999999999911")
      .body(NOTE_2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Can not find user 119999"));
  }

  @Test
  public void shouldReturn400WhenUserLookupFailsWithAuthorizationError() {
    // Simulate permission problem in user lookup
    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "22999999-9999-4999-9999-999999999922")
      .body(NOTE_2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("User lookup failed with 403. 229999"));
  }

  @Test
  public void shouldReturn400WhenUserIsRetrievedWithoutNecessaryFields() {
    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "33999999-9999-4999-9999-999999999933")
      .body(NOTE_2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Missing fields"));
  }

  @Test
  public void shouldReturn422WhenPostHasIdThatAlreadyExists() {
    sendOkPostRequest(NOTE_2, USER8);
    // Post the same id again
    sendPostRequest(NOTE_2, USER8)
      .statusCode(422)
      .body(containsString("violates unique constraint"));
  }

  @Test
  public void shouldReturn422WhenUpdatingNoteWithNotMatchingId() {
    givenWithUrl()
      .header(TEN).header(USER8).header(JSON)
      .body(UPDATE_NOTE_REQUEST)
      .put("/notes/22222222-2222-2222-a222-222222222222") // wrong one
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Can not change Id"));
  }


  @Test
  public void shouldReturn422WhenUpdatingNoteWithInvalidId() {
    givenWithUrl()
      .header(TEN).header(USER8).header(JSON)
      .body(UPDATE_NOTE_REQUEST)
      .put("/notes/11111111-222-1111-2-111111111111") // invalid UUID
      .then()
      .log().ifValidationFails()
      .statusCode(422);  // fails the same-id check before validating the UUID
  }

  @Test
  public void shouldReturn404WhenUpdatingNoteThatDoesntExist() {
    givenWithUrl() // not found
      .header(TEN).header(USER8).header(JSON)
      .body(UPDATE_NOTE_REQUEST.replaceAll("1", "3"))
      .put("/notes/33333333-3333-3333-a333-333333333333")
      .then()
      .log().ifValidationFails()
      .body(containsString("333 not found"))
      .statusCode(404);
  }

  @Test
  public void shouldUpdateNote() {
    sendOkPostRequest(NOTE_1, USER8);
    givenWithUrl() // This should work
      .header(TEN).header(USER8).header(JSON)
      .body(UPDATE_NOTE_REQUEST)
      .put("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(204);

    givenWithUrl()
      .header(TEN)
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("with a comment"))
      //.body(containsString("creatorUserNa=me"))
      //.body(containsString("creatorLastName"))
      .body(containsString("-8888-"));   // updated by
    // The creator fields should be there, once we mark them read-only, and
    // the RMB keeps such in place. MODNOTES-31
  }

  @Test
  public void shouldUpdateNoteWhenPutRequestIsSameAsGetResponse() {
    sendOkPostRequest(NOTE_2, USER8);
    // Update note, by fetching and PUTting back
    String rawNote2 = givenWithUrl()
      .header(TEN)
      .get("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().all() //.ifValidationFails()
      .statusCode(200)
      .extract().body().asString();
    String newNote2 = rawNote2
      .replaceAll("8888", "9999") // createdBy
      .replaceFirst("23456", "34567") // link to the thing
      .replaceAll("things", "rooms") // new domain (also in link)
      .replaceFirst("\"m8\"", "\"NewCrUsername\""); // readonly field, should not matter

    logger.info("About to PUT note: " + newNote2);

    givenWithUrl() // ok update
      .header(TEN).header(USER7).header(JSON)
      .body(newNote2)
      .put("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().ifValidationFails()
      .statusCode(204);
  }

  @Test
  public void shouldReturn400WhenDeleteRequestHasInvalidUUID() {
    givenWithUrl() // Bad UUID
      .header(TEN)
      .delete("/notes/11111111-3-1111-333-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(400);
  }

  @Test
  public void shouldReturn404WhenDeleteRequestHasNotExistingUUID() {
    givenWithUrl() // not found
      .header(TEN)
      .delete("/notes/11111111-2222-3333-a444-555555555555")
      .then()
      .log().ifValidationFails()
      .statusCode(404);
  }

  @Test
  public void shouldDeleteNote() {
    sendOkPostRequest(NOTE_1, USER9);
    givenWithUrl()
      .header(TEN)
      .delete("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(204);

    givenWithUrl()
      .header(TEN)
      .delete("/notes/11111111-1111-1111-a111-111111111111") // no longer there
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));
  }

  @Test
  public void shouldGenerateUuidInPostRequestIfItIsNotSet() {
    sendOkPostRequest(NOTE_3, USER9);
    // Fetch the note in various ways
    givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"id\" :")) // one given by the module
      .body(containsString("no id"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));
  }

  @Test
  public void shouldGetNotesWhenLimitIs1001() {
    sendOkPostRequest(NOTE_3, USER9);
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=title=testing&limit=1001")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"id\" :")) // one given by the module
      .body(containsString("no id"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));
  }

  @Test
  public void shouldReturn400WhenOffsetIsInvalid() {
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=title=testings&offset=-1")
      .then()
      .log().ifValidationFails()
      .statusCode(400);
  }

  @Test
  public void shouldReturn400WhenLimitIsInvalid() {
    givenWithUrl()
      .header(TEN)
      .get("/notes?query=title=testing&limit=-1")
      .then()
      .log().ifValidationFails()
      .statusCode(400);
  }

  @Test
  public void shouldDeleteNoteByPathReturnedFromPost() {
    final String location = sendPostRequest(NOTE_3, USER9)
      .statusCode(201)
      .extract().header("Location");

    givenWithUrl()
      .header(TEN)
      .delete(location)
      .then()
      .log().ifValidationFails()
      .statusCode(204);
  }

  private void sendOkPostRequest(String noteJson, Header userHeader) {
    sendPostRequest(noteJson, userHeader)
      .statusCode(201);
  }

  private ValidatableResponse sendPostRequest(String noteJson, Header userHeader) {
    return givenWithUrl()
      .header(TEN).header(userHeader).header(JSON)
      .body(noteJson)
      .post("/notes")
      .then()
      .log().ifValidationFails();
  }

  private RequestSpecification givenWithUrl() {
    return given()
      .header(new Header("X-Okapi-Url", getWiremockUrl()));
  }

  private String getWiremockUrl() {
    return HOST + ":" + userMockServer.port();
  }
}
