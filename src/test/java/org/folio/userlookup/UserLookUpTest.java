package org.folio.userlookup;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import static org.folio.test.util.TokenTestUtil.generateToken;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.test.junit.TestStartLoggingRule;
import org.folio.test.util.TestUtil;

@RunWith(VertxUnitRunner.class)
public class UserLookUpTest {

  private static final String STUB_TENANT = "testlib";
  private static final String host = "http://127.0.0.1";
  private static final String USER_INFO_STUB_FILE = "responses/userlookup/mock_user_response_200.json";
  private static final Map<String, String> OKAPI_HEADERS = new HashMap<>();
  private final String GET_USER_ENDPOINT = "/users/";

  private final UserLookUpService userLookUpService = new UserLookUpService();

  @Rule
  public TestRule watcher = TestStartLoggingRule.instance();
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

    OKAPI_HEADERS.put(XOkapiHeaders.TENANT, STUB_TENANT);
    OKAPI_HEADERS.put(XOkapiHeaders.URL, getWiremockUrl());
    OKAPI_HEADERS.put(XOkapiHeaders.TOKEN, generateToken("user", stubUserId));

    stubFor(
      get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(TestUtil.readFile(USER_INFO_STUB_FILE))));

    Future<UserLookUp> info = userLookUpService.getUserInfo(OKAPI_HEADERS);
    info.compose(userInfo -> {
      context.assertNotNull(userInfo);

      context.assertEquals("cedrick", userInfo.getUserName());
      context.assertEquals("firstname_test", userInfo.getFirstName());
      context.assertNull(userInfo.getMiddleName());
      context.assertEquals("lastname_test", userInfo.getLastName());

      async.complete();

      return null;
    }).otherwise(throwable -> {
      context.fail(throwable);
      async.complete();
      return null;
    });
  }

  @Test
  public void ShouldReturn401WhenUnauthorizedAccess(TestContext context) {
    final String stubUserId = "a49cefad-7447-4f2f-9004-de32e7a6cc53";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();

    OKAPI_HEADERS.put(XOkapiHeaders.URL, getWiremockUrl());
    OKAPI_HEADERS.put(XOkapiHeaders.TOKEN, generateToken("user", stubUserId));

    stubFor(
      get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(401)
          .withStatusMessage("Authorization Failure")));

    Future<UserLookUp> info = userLookUpService.getUserInfo(OKAPI_HEADERS);
    info.compose(result -> {
      context.assertNull(result);
      async.complete();
      return null;
    }).otherwise(exception -> {
      context.assertTrue(exception instanceof NotAuthorizedException);
      async.complete();
      return null;
    });
  }

  @Test
  public void ShouldReturn404WhenUserNotFound(TestContext context) {
    final String stubUserId = "xyz";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();

    OKAPI_HEADERS.put(XOkapiHeaders.TENANT, STUB_TENANT);
    OKAPI_HEADERS.put(XOkapiHeaders.URL, getWiremockUrl());
    OKAPI_HEADERS.put(XOkapiHeaders.TOKEN, generateToken("user", stubUserId));

    stubFor(
      get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(404)
          .withStatusMessage("User Not Found")));

    Future<UserLookUp> info = userLookUpService.getUserInfo(OKAPI_HEADERS);
    info.compose(result -> {
      context.assertNull(result);
      async.complete();
      return null;
    }).otherwise(exception -> {
      context.assertTrue(exception instanceof NotFoundException);
      async.complete();
      return null;
    });
  }

  @Test
  public void missingOkapiURLHeaderShouldReturn500Test(TestContext context) {
    Async async = context.async();

    OKAPI_HEADERS.put(XOkapiHeaders.TENANT, STUB_TENANT);

    Future<UserLookUp> info = userLookUpService.getUserInfo(OKAPI_HEADERS);
    info.compose(result -> {
      context.assertNull(result);
      async.complete();
      return null;
    }).otherwise(exception -> {
      context.assertTrue(exception instanceof IllegalStateException);
      async.complete();
      return null;
    });
  }

  /**
   * Returns url of Wiremock server used in this test
   */
  private String getWiremockUrl() {
    return host + ":" + userMockServer.port();
  }
}
