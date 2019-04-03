
package org.folio.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.client.TenantClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.userlookup.UserLookUpTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.google.common.io.Files;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TestBase {

  public static final String STUB_TENANT = "testlib";
  private static final Logger logger = LoggerFactory.getLogger(UserLookUpTest.class);
  private static final String STUB_TOKEN = "TEST_OKAPI_TOKEN";
  private static final String host = "http://127.0.0.1";
  private static final String HTTP_PORT = "http.port";
  private static final int port = NetworkUtils.nextFreePort();
  protected static Vertx vertx;
  
  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @BeforeClass
  public static void setup() throws IOException {

    vertx = Vertx.vertx();

    logger.info("Start vertx");
    DeploymentOptions restVerticleDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put(HTTP_PORT, port));

    startVerticle(restVerticleDeploymentOptions);

    postTenant(restVerticleDeploymentOptions);
  }

  private static void startVerticle(DeploymentOptions restVerticleDeploymentOptions) {

    logger.info("Start verticle");

    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), restVerticleDeploymentOptions, event -> future.complete(null));
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
    CompletableFuture<Void> future = new CompletableFuture<>();
    vertx.close(res -> {
      future.complete(null);
    });
    future.join();
  }

  protected RequestSpecification getRequestSpecification(String userId) {
    return new RequestSpecBuilder()
      .addHeader(XOkapiHeaders.TENANT, STUB_TENANT)
      .addHeader(XOkapiHeaders.TOKEN, STUB_TOKEN)
      .addHeader(XOkapiHeaders.URL, getWiremockUrl())
      .setBaseUri(host + ":" + port)
      .setPort(port)
      .log(LogDetail.ALL)
      .build();
  }
  
 
  public static void mockGet(StringValuePattern urlPattern, String responseFile) throws IOException, URISyntaxException {
    stubFor(get(new UrlPathPattern(urlPattern, (urlPattern instanceof RegexPattern)))
      .willReturn(new ResponseDefinitionBuilder()
        .withBody(readFile(responseFile))));
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
    return new File(TestBase.class.getClassLoader()
      .getResource(filename).toURI());
  }

  /**
   * Returns url of Wiremock server used in this test
   */
  private String getWiremockUrl() {
    return host + ":" + userMockServer.port();
  }
}