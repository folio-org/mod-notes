package org.folio.rest;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.client.TenantClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;

public class TestBase {

  public static final String STUB_TENANT = "testlib";

  protected static final Header JSON_CONTENT_TYPE_HEADER = new Header(HttpHeaders.CONTENT_TYPE,
    ContentType.APPLICATION_JSON.getMimeType());
  
  private static final Logger logger = LoggerFactory.getLogger(TestBase.class);
  
  private static final String STUB_TOKEN = "TEST_OKAPI_TOKEN";
  private static final String host = "http://127.0.0.1";
  private static final String HTTP_PORT = "http.port";
  private static final int port = NetworkUtils.nextFreePort();
  
  protected static Vertx vertx;

  @Rule
  public TestRule watcher = new TestWatcher() {

    @Override
    protected void starting(Description description) {
      logger.info("********** Running test method: {}.{} ********** ", description.getClassName(),
        description.getMethodName());
    }

  };
  
  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @BeforeClass
  public static void setup() throws IOException {

    vertx = Vertx.vertx();

    logger.info("Start embedded database");
    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();

    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put(HTTP_PORT, port));

    startVerticle(options);

    postTenant(options);
  }

  private static void startVerticle(DeploymentOptions options) {

    logger.info("Start verticle");

    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), options, event -> future.complete(null));
    future.join();
  }

  private static void postTenant(DeploymentOptions options) {

    logger.info("Post tenant");

    TenantClient tenantClient = new TenantClient(host + ":" + port, STUB_TENANT, STUB_TOKEN);

    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      try {
        tenantClient.postTenant(null, res2 -> future.complete(null));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    future.join();
  }

  @AfterClass
  public static void tearDown(){

    logger.info("Stop embedded database");

    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.close(res -> {
      PostgresClient.stopEmbeddedPostgres();
      future.complete(null);
    });
    future.join();
  }

  protected RequestSpecification getRequestSpecification() {
    return new RequestSpecBuilder()
      .addHeader(XOkapiHeaders.TENANT, STUB_TENANT)
      .addHeader(XOkapiHeaders.TOKEN, STUB_TOKEN)
      .addHeader(XOkapiHeaders.URL, getWiremockUrl())
      .setBaseUri(host + ":" + port)
      .setPort(port)
      .log(LogDetail.ALL)
      .build();
  }

  /**
   * Returns url of Wiremock server used in this test
   */
  private String getWiremockUrl() {
    return host + ":" + userMockServer.port();
  }

  protected ValidatableResponse getResponse(String resourcePath) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(resourcePath)
      .then();
  }

  protected ExtractableResponse<Response> getWithOk(String resourcePath) {
    return getWithStatus(resourcePath, HttpStatus.SC_OK);
  }

  protected ExtractableResponse<Response> getWithStatus(String resourcePath, int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get(resourcePath)
      .then()
      .statusCode(expectedStatus).extract();
  }

  protected ExtractableResponse<Response> putWithStatus(String resourcePath, String putBody,
                                                        int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(putBody)
      .when()
      .put(resourcePath)
      .then()
      .statusCode(expectedStatus)
      .extract();
  }

  protected ExtractableResponse<Response> postWithStatus(String resourcePath, String postBody,
                                                         int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(postBody)
      .when()
      .post(resourcePath)
      .then()
      .statusCode(expectedStatus)
      .extract();
  }

  protected ExtractableResponse<Response> deleteWithStatus(String resourcePath, int expectedStatus) {
    return RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .delete(resourcePath)
      .then()
      .statusCode(expectedStatus)
      .extract();
  }
}
