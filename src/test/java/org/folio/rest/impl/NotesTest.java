package org.folio.rest.impl;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.google.common.io.Files;
import com.jayway.restassured.RestAssured;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.jayway.restassured.RestAssured.given;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.specification.RequestSpecification;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import org.junit.Before;
import org.junit.runner.RunWith;

import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;

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

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private static final String LS = System.lineSeparator();
  private static final String HOST = "http://127.0.0.1";
  private final Header TEN = new Header("X-Okapi-Tenant", "modnotestest");
  private final Header ALLPERM = new Header("X-Okapi-Permissions", "notes.domain.all");
  private final Header USER9 = new Header("X-Okapi-User-Id",
    "99999999-9999-4999-9999-999999999999");
  private final Header USER19 = new Header("X-Okapi-User-Id",
    "11999999-9999-4999-9999-999999999911");  // One that is not found in the mock data
  private final Header USER8 = new Header("X-Okapi-User-Id",
    "88888888-8888-4888-8888-888888888888");
  private final Header USER7 = new Header("X-Okapi-User-Id",
    "77777777-7777-4777-a777-777777777777");
  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName; //  "mod-notes"
  private String moduleVersion; // "1.0.0" or "0.1.2-SNAPSHOT"
  private String moduleId; // "mod-notes-1.0.1-SNAPSHOT"
  Vertx vertx;
  Async async;

  private static int port;

  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @Before
  public void setUp(TestContext context) throws IOException, URISyntaxException {
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
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("notesTest: setup done. Using port " + port);


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
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after ModuleTest");
    async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
    }));
  }

  /**
   * All the tests. In one long function, because starting up the embedded
   * postgres takes so long and fills the log.
   *
   * @param context
   */
  @Test
  public void tests(TestContext context) {
    async = context.async();
    logger.info("notesTest starting");


    // Simple GET request to see the module is running and we can talk to it.
    givenWithUrl()
      .get("/admin/health")
      .then()
      .log().all()
      .statusCode(200);

    // Simple GET request without a tenant
    givenWithUrl()
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Tenant"));


    // Simple GET without notes.domains.* permissions
    givenWithUrl()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(401)
      .body(containsString("notes.domain"));


    // Simple GET request with a tenant, but before
    // we have invoked the tenant interface, so the
    // call will fail (with lots of traces in the log)
    givenWithUrl()
      .header(TEN)
      .header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(401);

    // Call the tenant interface to initialize the database
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    logger.info("About to call the tenant interface " + tenants);
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(tenants)
      .post("/_/tenant")
      .then()
      .log().ifValidationFails()
      .statusCode(201);

    // Empty list of notes
    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));

    // Post some malformed notes
    String bad1 = "This is not json";
    givenWithUrl()
      .header(TEN) // no content-type header
      .body(bad1)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Content-type"));

    givenWithUrl()
      .header(TEN).header(JSON)
      .body(bad1)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Json content error"));


    String note1 = "{"
      + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
      + "\"type\" : \"test note\"," + LS
      + "\"title\" : \"test note title\"," + LS
      + "\"content\" : \"First note email@folio.org\"}" + LS;
    // no domain, we add that when updating. This will break when we make
    // the domain required, just add the field here.
    // The email is to check that we don't trigger userId tag lookup for such

    String bad2 = note1.replaceFirst("}", ")"); // make it invalid json
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(bad2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Json content error"));

    String bad3 = note1.replaceFirst("type", "status").replaceFirst("test note", "ASSIGNED");
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(bad3)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      // English error message for Locale.US, see @Before
      .body("errors[0].message", is("may not be null"))
      .body("errors[0].parameters[0].key", is("type"));

    String badfieldDoc = note1.replaceFirst("type", "UnknownFieldName");
    givenWithUrl()
      .header(TEN).header(JSON)
      .body(badfieldDoc)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Unrecognized field"));

    // Post by an unknown user 19, lookup fails
    givenWithUrl()
      .header(TEN).header(USER19).header(JSON).header(ALLPERM)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    String bad4 = note1.replaceAll("-1111-", "-2-");  // make bad UUID
    givenWithUrl()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(bad4)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("invalid input syntax for type uuid"));

    // Post without permission
    givenWithUrl()
      .header(TEN).header(USER9).header(JSON)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(401)
      .body(containsString("No permission notes.domain.all"));

    // Post a good note
    givenWithUrl()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(201);


    // Fetch the note in various ways
    givenWithUrl()
      .header(TEN).header(ALLPERM)
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

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes/99111111-1111-1111-a111-111111111199")
      .then()
      .log().ifValidationFails()
      .statusCode(404)
      .body(containsString("not found"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes/777")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=content=fiRST")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("id"));


    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.createdByUserId=*9999*")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.createdByUserId=\"99999999-9999-4999-9999-999999999999\"")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=VERYBADQUERY")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("no serverChoiceIndexes defined"));


    // Why do the next two not fail with a QueryValidationException ??
    // When run manually (run.sh), they return a 422 all right
    // See MODNOTES-15, and the comments in NotesResourceImpl.java around initCQLValidation()
    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.UNKNOWNFIELD=foobar")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("is not present in index"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=UNKNOWNFIELD=foobar")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("is not present in index"));


    // Post another note, with a few tags to be notified
    String note2 = "{"
      + "\"id\" : \"22222222-2222-2222-a222-222222222222\"," + LS
      + "\"type\" : \"High Priority\"," + LS
      + "\"title\" : \"things\"," + LS
      + "\"content\" : \"Test on things\"}" + LS;

    // Wrong permissions, should fail
    givenWithUrl()
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.UNKNOWN,notes.domain.users")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .body(containsString("notes.domain.all"))
      .statusCode(401);


    // No userid, should fail
    givenWithUrl()
      .header(TEN).header(JSON)
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .body(containsString("Can not look up user"))
      .statusCode(400);

    // Simulate user lookup failure
    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "11999999-9999-4999-9999-999999999911")
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Can not find user 119999"));

    // Simulate permission problem in user lookup

    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "22999999-9999-4999-9999-999999999922")
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("User lookup failed with 403. 229999"));

    // Simulate user lookup with critical fields missing
    givenWithUrl()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "33999999-9999-4999-9999-999999999933")
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Missing fields"));


    // good permission, this should work
    givenWithUrl()
      .header(TEN).header(USER8).header(JSON)
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(201);


    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .statusCode(200)
      .log().ifValidationFails()
      .body(containsString("-8888-")) // metadata.createdByUserId
      .body(containsString("lastName"))
      .body(containsString("createdByUsername"))
      .body(containsString("things"));


    // Post the same id again
    givenWithUrl()
      .header(TEN).header(USER8).header(JSON)
      .header(ALLPERM)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("violates unique constraint"));


    // Get both notes a few different ways
    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=content=note")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("First note"));


    // Update a note
    //  no Creator fields, RMB should keep them, once we mark them as read-only
    String updated1 = "{"
      + "\"id\" : \"11111111-1111-1111-a111-111111111111\"," + LS
      + "\"type\" : \"low priority\"," + LS
      + "\"title\" : \"more things\"," + LS
      + "\"content\" : \"First note with a comment\"}" + LS;

    givenWithUrl()
      .header(TEN).header(USER8).header(JSON).header(ALLPERM)
      .body(updated1)
      .put("/notes/22222222-2222-2222-a222-222222222222") // wrong one
      .then()
      .log().ifValidationFails()
      .statusCode(422)
      .body(containsString("Can not change Id"));


    givenWithUrl()
      .header(TEN).header(USER8).header(JSON).header(ALLPERM)
      .body(updated1)
      .put("/notes/11111111-222-1111-2-111111111111") // invalid UUID
      .then()
      .log().ifValidationFails()
      .statusCode(422);  // fails the same-id check before validating the UUID


    givenWithUrl() // no domain permission
      .header(TEN).header(USER8).header(JSON)
      .body(updated1)
      .put("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .body(containsString("notes.domain.all"))
      .statusCode(401);

    givenWithUrl() // not found
      .header(TEN).header(USER8).header(JSON)
      .header(ALLPERM)
      .body(updated1.replaceAll("1", "3"))
      .put("/notes/33333333-3333-3333-a333-333333333333")
      .then()
      .log().ifValidationFails()
      .body(containsString("333 not found"))
      .statusCode(404);

    givenWithUrl() // This should work
      .header(TEN).header(USER8).header(JSON)
      .header(ALLPERM)
      .body(updated1)
      .put("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(204);


    givenWithUrl()
      .header(TEN).header(ALLPERM)
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

    // Update the other one, by fetching and PUTting back
    String rawNote2 = givenWithUrl()
      .header(TEN).header(ALLPERM)
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
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .body(newNote2)
      .put("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().ifValidationFails()
      .statusCode(204);

    /*

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().all() // ifValidationFails()
      .body(containsString("rooms")) // domain got updated
      // The RMB should manage the metadata
      .body(containsString("-8888-")) // createdBy, NOT CHANGED
      .body(containsString("-7777-")) // updatedBy, Should be changed
      // But the special fields are managed by our mod-notes. Should remain.
      .body(containsString("creatorUserName"))
      .body(containsString("creatorLastName"))
      .body(containsString("m8")); // CreatorUserName we tried to change



    // Check a PUT without id is accepted, uses the id from the url
    String OkNoteNoId = newNote2.replaceFirst("\"id\" : \"[2-]+\",", "");
    given() // ok update
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .body(OkNoteNoId)
      .put("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().ifValidationFails()
      .statusCode(204);
    given()
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .get("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().all() //ifValidationFails()
      .statusCode(200)
      .body(containsString("\"id\" : \"22222222-2222-2222-a222-222222222222\""))
      .body(containsString("-8888-")) // createdBy, NOT CHANGED
      .body(containsString("-7777-")) // updatedBy, Should be changed
      // But the special fields are managed by our mod-notes. Should remain.
      .body(containsString("creatorUserName"))
      .body(containsString("creatorLastName"))
      .body(containsString("m8")); // CreatorUserName we tried to change

    // check with extra permissions and all
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.all,notes.domain.extra")
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    // Check with extra permissions
    String perms = "notes.domain.users,notes.domain.rooms,notes.collection.get,"
      + "some.other.perm,privatenotes.domain.all";
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", perms)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200);
    */

    // Permission tests
    // Normally Okapi and mod-auth would provide the X-Okapi-Permissions
    // header. Here we run without Okapi, so we can set them up as needed.
    // Note that we are only testing permissionsDesired, which come through
    // as X-Okapi-Permissions. Required permissions are always filtered out
    // the hard way, and if not there, the module will never see the request.
    //
    /*
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.UNKNOWN,notes.domain.rooms")
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 1"))
      .body(containsString("rooms"));  // no users note!

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.meetingrooms,notes.domain.users")
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("users/1234"));

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.all")
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("users/1234"));

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .get("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(401)
      .body(containsString("notes.domain.users"));
      */

    // Failed deletes
    givenWithUrl() // Bad UUID
      .header(TEN)
      .delete("/notes/11111111-3-1111-333-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    givenWithUrl() // not found
      .header(TEN).header(ALLPERM)
      .delete("/notes/11111111-2222-3333-a444-555555555555")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    givenWithUrl() // wrong perm
      .header(TEN)
      .delete("/notes/11111111-1111-1111-a111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(401);

    // delete them
    givenWithUrl()
      .header(TEN)
      .header(ALLPERM)
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
      .header(TEN).header(ALLPERM)
      .delete("/notes/22222222-2222-2222-a222-222222222222")
      .then()
      .log().ifValidationFails()
      .statusCode(204);

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));

    // Test that we create the id if missing
    String note3 = "{"
      + "\"type\" : \"test type\"," + LS
       + "\"title\" : \"testing\"," + LS
      + "\"content\" : \"Note with no id\"}" + LS;

    final String location = givenWithUrl()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(note3)
      .post("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(201)
      .extract().header("Location");

    // Fetch the note in various ways
    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"id\" :")) // one given by the module
      .body(containsString("no id"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=title=testing&limit=1001")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"id\" :")) // one given by the module
      .body(containsString("no id"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=title=testings&offset=-1")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=title=testing&limit=-1")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    givenWithUrl()
      .header(TEN).header(ALLPERM)
      .delete(location)
      .then()
      .log().ifValidationFails()
      .statusCode(204);

    // All done
    logger.info("notesTest done");
    async.complete();
  }

  private RequestSpecification givenWithUrl() {
    return given()
      .header(new Header("X-Okapi-Url", getWiremockUrl()));
  }

  private String getWiremockUrl() {
    return HOST + ":" + userMockServer.port();
  }

  /**
   * Reads file from classpath as String
   */
  public static String readFile(String filename) throws IOException, URISyntaxException {
    return Files.asCharSource(getFile(filename), StandardCharsets.UTF_8).read();
  }

  /**
   * Returns File object corresponding to the file on classpath with specified filename
   */
  public static File getFile(String filename) throws URISyntaxException {
    return new File(NotesTest.class.getClassLoader()
      .getResource(filename).toURI());
  }
}
