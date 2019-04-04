package org.folio.userlookup;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.google.common.io.Files;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UserLookUpTest {
  public static final String STUB_TENANT = "testlib";
  private static final Logger logger = LoggerFactory.getLogger(UserLookUpTest.class);
  private static final String STUB_TOKEN = "TEST_OKAPI_TOKEN";
  private static final String host = "http://127.0.0.1";
  private static final String HTTP_PORT = "http.port";
  private static final int port = NetworkUtils.nextFreePort();
  protected static Vertx vertx;
  public static Map<String, String> okapiHeaders = new HashMap<>();
  private final String GET_USER_ENDPOINT = "/users/";
  private static final String USER_INFO_STUB_FILE = "responses/userlookup/mock_user_response_200.json";
  
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
  
  @Test
  public void shouldReturn200WhenUserIdIsValid(TestContext context) throws IOException, URISyntaxException {
    final String stubUserId = "a49cefad-7447-4f2f-9004-de32e7a6cc53";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();
    
    okapiHeaders.put("X-Okapi-tenant", STUB_TENANT);
    okapiHeaders.put("X-Okapi-Url", getWiremockUrl());
    okapiHeaders.put("X-Okapi-User-Id", stubUserId);
    
    stubFor(
        get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
            .withBody(readFile(USER_INFO_STUB_FILE))));
    
    CompletableFuture<UserLookUp> info = UserLookUp.getUserInfo(okapiHeaders);
    info.whenComplete((result, exception) -> {
      context.assertNotNull(result);

      UserLookUp userInfo = result;
      context.assertEquals("cedrick", userInfo.getUserName());
      context.assertEquals("firstname_test", userInfo.getFirstName());
      context.assertNull(userInfo.getMiddleName());
      context.assertEquals("lastname_test", userInfo.getLastName());

      async.complete();
    }).exceptionally(throwable -> {
      context.fail(throwable);
      async.complete();
      return null;
    });
  }
  
  @Test
  public void ShouldReturn401WhenUnauthorizedAccess(TestContext context) throws IOException, URISyntaxException {
    final String stubUserId = "a49cefad-7447-4f2f-9004-de32e7a6cc53";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();
    
    okapiHeaders.put("X-Okapi-Url", getWiremockUrl());
    okapiHeaders.put("X-Okapi-User-Id", stubUserId);
    
    stubFor(
        get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
            .withStatus(401)
            .withStatusMessage("Authorization Failure")));
    
    CompletableFuture<UserLookUp> info = UserLookUp.getUserInfo(okapiHeaders);
    info.whenComplete((result, exception) -> {
      context.assertNull(result);
      context.assertTrue(exception.getCause() instanceof NotAuthorizedException);
    
      async.complete();
    });
  }
  
  @Test
  public void ShouldReturn404WhenUserNotFound(TestContext context) throws IOException, URISyntaxException {
    final String stubUserId = "xyz";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();
    
    okapiHeaders.put("X-Okapi-tenant", STUB_TENANT);
    okapiHeaders.put("X-Okapi-Url", getWiremockUrl());
    okapiHeaders.put("X-Okapi-User-Id", stubUserId);
    
    stubFor(
        get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
            .withStatus(404)
            .withStatusMessage("User Not Found")));
    
    CompletableFuture<UserLookUp> info = UserLookUp.getUserInfo(okapiHeaders);
    info.whenComplete((result, exception) -> {
      context.assertNull(result);
      context.assertTrue(exception.getCause() instanceof NotFoundException);
    
      async.complete();
    });
  }
  
  @Test
  public void missingOkapiURLHeaderShouldReturn500Test(TestContext context) {
    Async async = context.async();

    okapiHeaders.put("X-Okapi-tenant", STUB_TENANT);

    CompletableFuture<UserLookUp> info = UserLookUp.getUserInfo(okapiHeaders);
    info.whenComplete((result, exception) -> {
      context.assertNull(result);
      context.assertTrue(exception.getCause() instanceof IllegalStateException);

      async.complete();
    });
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
    return new File(UserLookUpTest.class.getClassLoader()
      .getResource(filename).toURI());
  }

  /**
   * Returns url of Wiremock server used in this test
   */
  private String getWiremockUrl() {
    return host + ":" + userMockServer.port();
  }
}