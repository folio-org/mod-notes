package org.folio.rest.impl;

import org.junit.Test;

import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.given;
import com.jayway.restassured.response.Header;
import static org.hamcrest.Matchers.containsString;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.runner.RunWith;

import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
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
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private static final String LS = System.lineSeparator();
  private final Header TEN = new Header("X-Okapi-Tenant", "modnotestest");
  private final Header ALLPERM = new Header("X-Okapi-Permissions", "notes.domain.all");
  private final Header USER9 = new Header("X-Okapi-User-Id",
    "99999999-9999-9999-9999-999999999999");
  private final Header USER19 = new Header("X-Okapi-User-Id",
    "11999999-9999-9999-9999-999999999911");  // One that is not found in the mock data
  private final Header USER8 = new Header("X-Okapi-User-Id",
    "88888888-8888-8888-8888-888888888888");
  private final Header USER7 = new Header("X-Okapi-User-Id",
    "77777777-7777-7777-7777-777777777777");
  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName; //  "mod-notes"
  private String moduleVersion; // "1.0.0" or "0.1.2-SNAPSHOT"
  private String moduleId; // "mod-notes-1.0.1-SNAPSHOT"
  Vertx vertx;
  Async async;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    moduleName = PomReader.INSTANCE.getModuleName()
      .replaceAll("_", "-");  // RMB normalizes the dash to underscore, fix back
    moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = moduleName + "-" + moduleVersion;
    logger.info("Test setup starting for " + moduleId);
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }

    JsonObject conf = new JsonObject()
      .put("http.port", port)
      .put(HttpClientMock2.MOCK_MODE, "true");
    logger.info("notesTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("notesTest: setup done. Using port " + port);
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
    given()
      .get("/admin/health")
      .then()
      .log().all()
      .statusCode(200);

    // Simple GET request without a tenant
    given()
      .get("/notes")
      .then()
      .log().all()
      .statusCode(400)
      .body(containsString("Tenant"));

    // Simpel GET without notes.domains.* permissions
    given()
      .header(TEN)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(401)
      .body(containsString("notes.domain"));

    // Simple GET request with a tenant, but before
    // we have invoked the tenant interface, so the
    // call will fail (with lots of traces in the log)
    given()
      .header(TEN)
      .header(ALLPERM)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(400)
      .body(containsString("\"modnotestest_mod_notes.note_data\" does not exist"));

    // Call the tenant interface to initialize the database
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    logger.info("About to call the tenant interface " + tenants);
    given()
      .header(TEN).header(JSON)
      .body(tenants)
      .post("/_/tenant")
      .then()
      .log().ifError()
      .statusCode(201);

    // Empty list of notes
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifError()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));

    // Post some malformed notes
    String bad1 = "This is not json";
    given()
      .header(TEN) // no content-type header
      .body(bad1)
      .post("/notes")
      .then()
      .statusCode(400)
      .body(containsString("Content-type"));

    given()
      .header(TEN).header(JSON)
      .body(bad1)
      .post("/notes")
      .then()
      .statusCode(400)
      .body(containsString("Json content error"));

    String note1 = "{"
      + "\"id\" : \"11111111-1111-1111-1111-111111111111\"," + LS
      + "\"link\" : \"users/1234\"," + LS
      + "\"text\" : \"First note email@folio.org\"}" + LS;
    // no domain, we add that when updating. This will break when we make
    // the domain required, just add the field here.
    // The email is to check that we don't trigger userId tag lookup for such

    String bad2 = note1.replaceFirst("}", ")"); // make it invalid json
    given()
      .header(TEN).header(JSON)
      .body(bad2)
      .post("/notes")
      .then()
      .statusCode(400)
      .body(containsString("Json content error"));

    String bad3 = note1.replaceFirst("link", "creatorMiddleName");
    given()
      .header(TEN).header(JSON)
      .body(bad3)
      .post("/notes")
      .then()
      .statusCode(422)
      .body(containsString("may not be null"))
      .body(containsString("\"link\","));

    // Post by an unknown user 19, lookup fails
    given()
      .header(TEN).header(USER19).header(JSON).header(ALLPERM)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(400);

    String bad4 = note1.replaceAll("-1111-", "-2-");  // make bad UUID
    given()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(bad4)
      .post("/notes")
      .then()
      .log().all()
      .statusCode(400)
      .body(containsString("invalid input syntax for uuid"));

    // Post a good note
    given()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(201);

    // Fetch the note in various ways
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("domain")) // Default created from link
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("Mockerson")) // CreatedByLastName
      .body(containsString("Mockey")) // CreatedByFirstName
      .body(containsString("M.")) // CreatedByMiddleName
      .body(containsString("\"totalRecords\" : 1"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/99111111-1111-1111-1111-111111111199")
      .then()
      .log().all()
      .statusCode(404)
      .body(containsString("not found"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/777")
      .then()
      .log().all()
      .statusCode(400);

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=text=fiRST")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.createdByUserId=9999")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.createdByUserId=\"99999999-9999-9999-9999-999999999999\"")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=VERYBADQUERY")
      .then()
      .log().all()
      .statusCode(422)
      .body(containsString("QueryValidationException"));

    // Why do the next two not fail with a QueryValidationException ??
    // When run manually (run.sh), they return a 422 all right
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=metadata.UNKNOWNFIELD=foobar")
      .then()
      .statusCode(200)
      .log().all()
      .body(containsString("\"totalRecords\" : 0"));

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=UNKNOWNFIELD=foobar")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 0"));

    // Post another note, with a few tags to be notified
    String note2 = "{"
      + "\"id\" : \"22222222-2222-2222-2222-222222222222\"," + LS
      + "\"link\" : \"things/23456\"," + LS
      + "\"domain\" : \"things\"," + LS
      + "\"text\" : \"@foo Note on a thing @üñí @bar\"," + LS
      + "\"creatorUserName\" : \"user88\" }" + LS;  // Different from mock

    // Wrong permissions, should fail
    given()
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.UNKNOWN,notes.domain.users")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .body(containsString("notes.domain.things"))
      .statusCode(401);

    // No userid, should fail
    given()
      .header(TEN).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .body(containsString("Can not look up user"))
      .statusCode(400);

    // Simulate user lookup failure
    given()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "11999999-9999-9999-9999-999999999911")
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(400)
      .body(containsString("Can not find user 119999"));

    // Simulate permission problem in user lookup
    given()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "22999999-9999-9999-9999-999999999922")
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(400)
      .body(containsString("User lookup failed with 403. 229999"));

    // Simulate user lookup with critical fields missing
    given()
      .header(TEN).header(JSON)
      .header("X-Okapi-User-Id", "33999999-9999-9999-9999-999999999933")
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(400)
      .body(containsString("Missing fields"));

    // Force an error in notify-post
    given()
      .header(TEN).header(JSON).header(USER8).header(ALLPERM)
      .body(note2.replaceAll("foo", "broken"))
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(500)
      .body(containsString("Notify post failed with 500"));

    // good permission, this should work
    given()
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(201);

    // Post the same id again
    given()
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(422)
      .body(containsString("Duplicate id"));

    // Get both notes a few different ways
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes?query=text=note")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("user88")) // the creator username we posted
      .body(containsString("things/23456"));

    // Update a note
    String updated1 = "{"
      + "\"id\" : \"11111111-1111-1111-1111-111111111111\"," + LS
      + "\"link\" : \"users/1234\"," + LS
      + "\"domain\" : \"users\"," + LS
      + "\"creatorUserName\" : \"user-newname-88\"," + LS
      + "\"creatorLastName\" : \"User8-changed\"," + LS
      + "\"text\" : \"First note with a comment to @foo\"}" + LS;

    given()
      .header(TEN).header(USER8).header(JSON).header(ALLPERM)
      .body(updated1)
      .put("/notes/22222222-2222-2222-2222-222222222222") // wrong one
      .then()
      .log().all()
      .statusCode(422)
      .body(containsString("Can not change the id"));

    given()
      .header(TEN).header(USER8).header(JSON).header(ALLPERM)
      .body(updated1)
      .put("/notes/11111111-222-1111-2-111111111111") // invalid UUID
      .then()
      .log().all()
      .statusCode(400);

    given() // no domain permission
      .header(TEN).header(USER8).header(JSON)
      .body(updated1)
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .body(containsString("notes.domain.users"))
      .statusCode(401);

    given() // wrong domain permission
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(updated1)
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .body(containsString("notes.domain.users"))
      .statusCode(401);

    given() // not found
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.users")
      .body(updated1.replaceAll("1", "3"))
      .put("/notes/33333333-3333-3333-3333-333333333333")
      .then()
      .log().ifError()
      .body(containsString("333 not found"))
      .statusCode(404);

    given() // No username
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.users")
      .body(updated1.replaceAll("creatorUserName", "creatorFirstName"))
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .body(containsString("creatorUserName is required"))
      .statusCode(422);

    given() // No last name
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.users")
      .body(updated1.replaceAll("creatorLastName", "creatorFirstName"))
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .body(containsString("creatorLastName is required"))
      .statusCode(422);

    given() // This should work
      .header(TEN).header(USER8).header(JSON)
      .header("X-Okapi-Permissions", "notes.domain.users")
      .body(updated1)
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .statusCode(204);

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .statusCode(200)
      .body(containsString("with a comment"))
      .body(containsString("-8888-"));   // updated by

    // Update the other one, by fetching and PUTting back
    String rawNote2 = given()
      .header(TEN).header(ALLPERM)
      .get("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().all() // .ifError()
      .statusCode(200)
      .extract().body().asString();
    String newNote2 = rawNote2
      .replaceAll("8888", "9999") // createdBy
      .replaceFirst("23456", "34567") // link to the thing
      .replaceFirst("\"things\"", "\"rooms\""); // new domain

    given() // no perm for rooms
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .body(newNote2)
      .put("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().ifError()
      .body(containsString("rooms"))
      .statusCode(401);
    given() // no perm for things
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .header("X-Okapi-Permissions", "notes.domain.rooms")
      .body(newNote2)
      .put("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().ifError()
      .statusCode(401);

    given() // ok update
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .body(newNote2)
      .put("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().ifError()
      .statusCode(204);
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().all() // .ifError()
      .body(containsString("-8888-"));   // createdBy, NOT CHANGED
    //.body(containsString("-9999-"));   // createdBy
    // The RMB will manage the metadata, and not change anything in it

    // Check a PUT without id is properly rejected - modnotes-22
    String badNote2 = rawNote2.replaceFirst("\"id\" : \"[2-]+\",", "");
    given() // ok update
      .header(TEN).header(USER7).header(JSON).header(ALLPERM)
      .body(badNote2)
      .put("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().ifError()
      .statusCode(422);

    // check with extra permissions and all
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.all,notes.domain.extra")
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200);

    // Check with extra permissions
    String perms = "notes.domain.users,notes.domain.rooms,notes.collection.get,"
      + "some.other.perm,privatenotes.domain.all";
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", perms)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200);

    // _self
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes/_self")
      .then()
      .statusCode(400)
      .log().all()
      .body(containsString("No UserId"));

    given()
      .header(TEN).header(USER9).header(ALLPERM)
      .get("/notes/_self")
      .then()
      .statusCode(200)
      .log().all()
      .body(containsString("with a comment"));

    given()
      .header(TEN).header(USER8).header(ALLPERM)
      .get("/notes/_self?query=text=note")
      .then()
      .log().all()
      .body(containsString("on a thing")); // createdby matches

    given()
      .header(TEN).header(USER8)
      .header("X-Okapi-Permissions", "notes.domain.things,notes.domain.rooms")
      .get("/notes/_self?query=text=note")
      .then()
      .log().all()
      .body(containsString("on a thing")); // createdby matches

    // Permission tests
    // Normally Okapi and mod-auth would provide the X-Okapi-Permissions
    // header. Here we run without Okapi, so we can set them up as needed.
    // Note that we are only testing permissionsDesired, which come through
    // as X-Okapi-Permissions. Required permissions are always filtered out
    // the hard way, and if not there, the module will never see the request.
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.UNKNOWN,notes.domain.rooms")
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 1"))
      .body(containsString("rooms"));  // no users note!

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.meetingrooms,notes.domain.users")
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("users/1234"));

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.all")
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("users/1234"));

    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().all()
      .statusCode(401)
      .body(containsString("notes.domain.users"));

    // Failed deletes
    given() // Bad UUID
      .header(TEN)
      .delete("/notes/11111111-3-1111-333-111111111111")
      .then()
      .log().all()
      .statusCode(400);

    given() // not found
      .header(TEN).header(ALLPERM)
      .delete("/notes/11111111-2222-3333-4444-555555555555")
      .then()
      .statusCode(404);

    given() // wrong perm
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.things")
      .delete("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .statusCode(401);

    // delete them
    given()
      .header(TEN)
      .header("X-Okapi-Permissions", "notes.domain.users")
      .delete("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .statusCode(204);

    given()
      .header(TEN)
      .delete("/notes/11111111-1111-1111-1111-111111111111") // no longer there
      .then()
      .statusCode(404);

    given()
      .header(TEN).header(ALLPERM)
      .delete("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .statusCode(204);

    given()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().ifError()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));

    // Test that we create the id if missing
    String note3 = "{"
      + "\"link\" : \"users/1234\"," + LS
      + "\"text\" : \"Note with no id\"}" + LS;

    final String location = given()
      .header(TEN).header(USER9).header(JSON).header(ALLPERM)
      .body(note3)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(201)
      .extract().header("Location");

    // Fetch the note in various ways
    given()
      .header(TEN).header(ALLPERM)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("\"id\" :")) // one given by the module
      .body(containsString("no id"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));

    given()
      .header(TEN).header(ALLPERM)
      .delete(location)
      .then()
      .log().ifError()
      .statusCode(204);

    // All done
    logger.info("notesTest done");
    async.complete();
  }


}
