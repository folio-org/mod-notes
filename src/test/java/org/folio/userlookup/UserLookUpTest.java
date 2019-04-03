package org.folio.userlookup;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UserLookUpTest extends TestBase {
  private final String GET_USER_ENDPOINT = "/users/";
  private static final String USER_INFO_STUB_FILE = "responses/userlookup/mock_user_response_200.json";
  
  @Test
  public void shouldReturn200WhenUserIdIsValid(TestContext context) throws IOException, URISyntaxException {
    final String stubUserId = "a49cefad-7447-4f2f-9004-de32e7a6cc53";
    final String stubUserIdEndpoint = GET_USER_ENDPOINT + stubUserId;
    Async async = context.async();

    stubFor(
        get(new UrlPathPattern(new RegexPattern(stubUserIdEndpoint), true))
        .willReturn(new ResponseDefinitionBuilder()
            .withBody(readFile(USER_INFO_STUB_FILE))));
    
    CompletableFuture<UserLookUp> info = UserLookUp.getUserInfo();
    info.whenComplete((result, exception) -> {
      context.assertNotNull(result);

      UserLookUp userInfo = result;
      context.assertEquals("", userInfo.getUserName());
      context.assertEquals("", userInfo.getFirstName());
      context.assertEquals("", userInfo.getMiddleName());
      context.assertEquals("", userInfo.getLastName());

      async.complete();
    }).exceptionally(throwable -> {
      context.fail(throwable);
      async.complete();
      return null;
    });
  }
}