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
  private final Header TEN = new Header("X-Okapi-Tenant", "modnotestest");
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

    logger.info("notesTest done");
    async.complete();
  }


}
