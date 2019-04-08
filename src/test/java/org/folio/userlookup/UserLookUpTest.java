package org.folio.userlookup;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import org.folio.rest.TestBase;
import org.folio.rest.impl.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UserLookUpTest {
  public static final String STUB_TENANT = "testlib";
  private static final String host = "http://127.0.0.1";
  protected static Vertx vertx;
  public static Map<String, String> okapiHeaders = new HashMap<>();
  private final String GET_USER_ENDPOINT = "/users/";
  private static final String USER_INFO_STUB_FILE = "responses/userlookup/mock_user_response_200.json";
  
  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));
  
  @Test
  public void shouldReturn200WhenUserIdIsValid(TestContext context) throws IOException, URISyntaxException {
    final String stubUserId = "a49cefad-7447-4f2f-9004-de32e7a6cc53";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();
    
    okapiHeaders.put("X-Okapi-tenant", TestBase.STUB_TENANT);
    okapiHeaders.put("X-Okapi-Url", getWiremockUrl());
    okapiHeaders.put("X-Okapi-User-Id", stubUserId);
    
    stubFor(
        get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
            .withBody(TestUtil.readFile(USER_INFO_STUB_FILE))));
    
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
   * Returns url of Wiremock server used in this test
   */
  private String getWiremockUrl() {
    return host + ":" + userMockServer.port();
  }
}