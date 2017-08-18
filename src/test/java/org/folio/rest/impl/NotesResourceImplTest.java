package org.folio.rest.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;
import static com.jayway.restassured.RestAssured.given;
import com.jayway.restassured.response.Header;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Before;
import org.junit.runner.RunWith;

import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
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
public class NotesResourceImplTest {
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private static final String LS = System.lineSeparator();
  private final Header TEN = new Header("X-Okapi-Tenant", "modnotestest");
  private final Header USER1 = new Header("X-Okapi-USer-Id",
    "99999999-9999-9999-9999-999999999999");
  private final Header USER2 = new Header("X-Okapi-USer-Id",
    "88888888-8888-8888-8888-888888888888");

  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName = "mod-notes";
  private String moduleVersion = "0.1.2-SNAPSHOT";
  private String moduleId = moduleName + "-" + moduleVersion;
  Vertx vertx;
  Async async;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    logger.info("NotesResourceImplTest: Setup starting");

    /*
     // Get versions. Seems not to work.
     InputStream in = getClass().getClassLoader().
      //getResourceAsStream("META-INF/maven/org.folio.okapi/okapi-core/pom.properties");
    getResourceAsStream("META-INF/maven/org.folio.rest/mod-notes/pom.properties");
    if (in != null) {
      try {
        Properties prop = new Properties();
        prop.load(in);
        in.close();
        moduleVersion = prop.getProperty("version");
        moduleName = prop.getProperty("artifactId");
      } catch (Exception e) {
        logger.warn(e);
      }
      logger.info("NotesResourceImplTest: '" + moduleName + "' '" + moduleVersion + "'");
    } else {
      logger.warn("NotesResourceImplTest: Setup could not read the version number");
    }
     */
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }

    JsonObject conf = new JsonObject()
      .put("http.port", port);

    logger.info("NotesResourceImplTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("NotesResourceImplTest: setup done. Using port " + port);
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
  public void notesTest(TestContext context) {
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

    // Simple GET request with a tenant, but before
    // we have invoked the tenant interface, so the
    // call will fail (with lots of traces in the log)
    given()
      .header(TEN)
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
      .header(TEN)
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
      + "\"text\" : \"First note\"}" + LS;

    String bad2 = note1.replaceFirst("}", ")"); // make it invalid json
    given()
      .header(TEN).header(JSON)
      .body(bad2)
      .post("/notes")
      .then()
      .statusCode(400)
      .body(containsString("Json content error"));

    String bad3 = note1.replaceFirst("link", "badFieldName");
    given()
      .header(TEN).header(JSON)
      .body(bad3)
      .post("/notes")
      .then()
      .statusCode(422)
      .body(containsString("may not be null"))
      .body(containsString("\"link\","));

    String bad4 = note1.replaceAll("-1111-", "-2-");  // make bad UUID
    given()
      .header(TEN).header(JSON)
      .body(bad4)
      .post("/notes")
      .then()
      .log().all()
      .statusCode(500); // Why does it crash on this ??
    // Files as RMB-53
    //.statusCode(422)
    //.body(containsString("invalid input syntax for uuid"));

    // Post a good note
    given()
      .header(TEN).header(USER1).header(JSON)
      .body(note1)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(201);

    // Fetch the note in various ways
    given()
      .header(TEN)
      .get("/notes")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("-9999-")) // CreatedBy userid in metadata
      .body(containsString("\"totalRecords\" : 1"));

    given()
      .header(TEN)
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    given()
      .header(TEN)
      .get("/notes/777")
      .then()
      .statusCode(500);  // Another funny 500-error on invalid UUIDS.

    given()
      .header(TEN)
      .get("/notes?query=text=fiRST")
      .then()
      .statusCode(200)
      .body(containsString("First note"));

    String note2 = "{"
      + "\"id\" : \"22222222-2222-2222-2222-222222222222\"," + LS
      + "\"link\" : \"things/23456\"," + LS
      + "\"text\" : \"Note on a thing\"}" + LS;

    // Post another note
    given()
      .header(TEN).header(USER2).header(JSON)
      .body(note2)
      .post("/notes")
      .then()
      .log().ifError()
      .statusCode(201);

    // Get both notes a few different ways
    given()
      .header(TEN)
      .get("/notes?query=text=note")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("First note"))
      .body(containsString("things/23456"));

    // Update a note
    String updated1 = "{"
      + "\"id\" : \"11111111-1111-1111-1111-111111111111\"," + LS
      + "\"link\" : \"users/1234\"," + LS
      + "\"text\" : \"First note with a comment\"}" + LS;

    given()
      .header(TEN).header(USER2).header(JSON)
      .body(updated1)
      .put("/notes/22222222-2222-2222-2222-222222222222") // wrong one
      .then()
      .log().ifError()
      .statusCode(422)
      .body(containsString("Can not change the id"));

    given()
      .header(TEN).header(USER2).header(JSON)
      .body(updated1)
      .put("/notes/55555555-5555-5555-5555-555555555555") // bad one
      .then()
      .log().ifError()
      .statusCode(422)
      .body(containsString("Can not change the id"));

    given()
      .header(TEN).header(USER2).header(JSON)
      .body(updated1)
      .put("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifError()
      .statusCode(204);

    given()
      .header(TEN)
      .get("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().all()
      .statusCode(200)
      .body(containsString("with a comment"))
      .body(containsString("-8888-"));   // updated by

    // _self
    given()
      .header(TEN).header(USER1)
      .get("/notes/_self")
      .then()
      .statusCode(200)
      .body(containsString("with a comment"));

    given()
      .header(TEN).header(USER2)
      .get("/notes/_self")
      .then()
      .log().all()
      .body(containsString("with a comment")) // editedby matches
      .body(containsString("on a thing")); // createdby matches

    // delete them
    given()
      .header(TEN)
      .delete("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .log().all()
      .statusCode(204);

    given()
      .header(TEN)
      .delete("/notes/11111111-1111-1111-1111-111111111111")
      .then()
      .statusCode(404);

    given()
      .header(TEN)
      .delete("/notes/22222222-2222-2222-2222-222222222222")
      .then()
      .log().all()
      .statusCode(204);

    given()
      .header(TEN)
      .get("/notes")
      .then()
      .log().ifError()
      .statusCode(200)
      .body(containsString("\"notes\" : [ ]"));

    // All done
    logger.info("notesTest done");
    async.complete();
  }


}
