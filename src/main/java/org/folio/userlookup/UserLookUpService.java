package org.folio.userlookup;

import static org.folio.rest.tools.utils.TenantTool.calculateTenantId;

import java.util.Map;
import java.util.Optional;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.TokenUtils;
import org.folio.util.UserInfo;

@Component
public class UserLookUpService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserLookUpService.class);

  private static final String USERS_ENDPOINT_TEMPLATE = "/users/%s";

  private static final String AUTHORIZATION_FAILURE_MESSAGE = "Authorization failure";
  private static final String USER_NOT_FOUND_MESSAGE = "User not found";
  private static final String CANNOT_GET_USER_DATA_MESSAGE = "Cannot get user data: %s";

  /**
   * Returns the user information for the userid specified in the x-okapi-token header.
   *
   * @param okapiHeaders The headers for the current API call.
   * @return User information based on userid from header.
   */
  public Future<UserLookUp> getUserInfo(final Map<String, String> okapiHeaders) {
    CaseInsensitiveMap<String, String> headers = new CaseInsensitiveMap<>(okapiHeaders);

    String token = calculateTenantId(headers.get(XOkapiHeaders.TOKEN));
    Optional<UserInfo> userInfo = TokenUtils.userInfoFromToken(token);

    return userInfo.isPresent()
      ? fetchUser(userInfo.get(), headers)
      : failedPromise();
  }

  private Future<UserLookUp> fetchUser(UserInfo userInfo, CaseInsensitiveMap<String, String> headers) {
    Promise<UserLookUp> promise = Promise.promise();
    final String tenantId = calculateTenantId(headers.get(XOkapiHeaders.TENANT));
    String okapiURL = headers.get(XOkapiHeaders.URL);
    String userId = userInfo.getUserId();
    String url = String.format(USERS_ENDPOINT_TEMPLATE, userId);
    try {
      final HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);
      httpClient.request(url, headers)
        .thenApply(response -> {
          try {
            if (Response.isSuccess(response.getCode())) {
              return mapUserInfo(response);
            } else if (response.getCode() == 401 || response.getCode() == 403) {
              LOGGER.error(AUTHORIZATION_FAILURE_MESSAGE);
              throw new NotAuthorizedException(AUTHORIZATION_FAILURE_MESSAGE);
            } else if (response.getCode() == 404) {
              LOGGER.error(USER_NOT_FOUND_MESSAGE);
              throw new NotFoundException(USER_NOT_FOUND_MESSAGE);
            } else {
              String msg = String.format(CANNOT_GET_USER_DATA_MESSAGE, response.getError());
              LOGGER.error(msg, response.getException());
              throw new IllegalStateException(response.getError().toString());
            }
          } finally {
            httpClient.closeClient();
          }
        })
        .thenAccept(promise::complete)
        .exceptionally(e -> {
          promise.fail(e.getCause());
          return null;
        });
    } catch (Exception e) {
      String msg = String.format(CANNOT_GET_USER_DATA_MESSAGE, e.getMessage());
      LOGGER.error(msg, e);
      promise.fail(e);
    }

    return promise.future();
  }

  private Future<UserLookUp> failedPromise() {
    Promise<UserLookUp> promise = Promise.promise();
    LOGGER.error(AUTHORIZATION_FAILURE_MESSAGE);
    promise.fail(new NotAuthorizedException(AUTHORIZATION_FAILURE_MESSAGE));
    return promise.future();
  }

  private UserLookUp mapUserInfo(Response response) {
    UserLookUp.UserLookUpBuilder builder = UserLookUp.builder();
    JsonObject user = response.getBody();
    if (user.containsKey("username") && user.containsKey("personal")) {
      builder.userName(user.getString("username"));

      JsonObject personalInfo = user.getJsonObject("personal");
      if (personalInfo != null) {
        builder.firstName(personalInfo.getString("firstName"));
        builder.middleName(personalInfo.getString("middleName"));
        builder.lastName(personalInfo.getString("lastName"));
      }
    } else {
      throw new BadRequestException("Missing fields");
    }
    return builder.build();
  }
}
