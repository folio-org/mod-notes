package org.folio.userlookup;

import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;

/**
 * Retrieves user information from mod-users /users/{userId} endpoint.
 */
public class UserLookUp {
  private static final Logger logger = LoggerFactory.getLogger(UserLookUp.class);

  private String userName;
  private String firstName;
  private String middleName;
  private String lastName;

  private UserLookUp() {
    super();
  }

 /**
   * Returns the username.
   *
   * @return UserName.
   */
  public String getUserName() {
    return userName;
  }

  /**
   * Returns the first name.
   *
   * @return FirstName.
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * Returns the middle name.
   *
   * @return MiddleName.
   */
  public String getMiddleName() {
    return middleName;
  }

  /**
   * Returns the last name.
   *
   * @return LastName.
   */
  public String getLastName() {
    return lastName;
  }

  @Override
  public String toString() {
    return "UserInfo [userName=" + userName
        + ", firstName=" + firstName + ", middleName=" + middleName + ", lastName=" + lastName + ']';
  }

  /**
   * Returns the user information for the userid specified in the original
   * request.
   *
   * @param okapiHeaders The headers for the current API call.
   * @return User information based on userid from header.
   */
  public static Future<UserLookUp> getUserInfo(final Map<String, String> okapiHeaders) {
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    headers.addAll(okapiHeaders);

    final String tenantId = TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
    final String userId = headers.get(RestVerticle.OKAPI_USERID_HEADER);
    Future<UserLookUp> future = Future.future();
    if (userId == null) {
      logger.error("No userid header");
      future.fail(new BadRequestException("Missing user id header, cannot look up user"));
      return future;
    }

    String okapiURL = headers.get(XOkapiHeaders.URL);
    String url = "/users/" + userId;
    try {
      final HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);
      httpClient.request(url, okapiHeaders)
        .thenApply(response -> {
          try {
            if (Response.isSuccess(response.getCode())) {
                return mapUserInfo(response);
            } else if (response.getCode() == 401 || response.getCode() == 403) {
                logger.error("Authorization failure");
                throw new NotAuthorizedException("Authorization failure");
            } else if (response.getCode() == 404) {
                logger.error("User not found");
                throw new NotFoundException("User not found");
            } else {
                logger.error("Cannot get user data: " + response.getError().toString(), response.getException());
                 throw new IllegalStateException(response.getError().toString());
            }
          } finally {
            httpClient.closeClient();
          }
        })
        .thenAccept(future::complete)
        .exceptionally(e -> {
          future.fail(e.getCause());
          return null;
        });
    } catch (Exception e) {
      logger.error("Cannot get user data: " + e.getMessage(), e);
      future.fail(e);
    }

    return future;
  }

  private static UserLookUp mapUserInfo(Response response) {
    UserLookUp userInfo = new UserLookUp();
    JsonObject user = response.getBody();
    if (user.containsKey("username") && user.containsKey("personal")) {
      userInfo.userName = user.getString("username");

      JsonObject personalInfo = user.getJsonObject("personal");
      if (personalInfo != null) {
        userInfo.firstName = personalInfo.getString("firstName");
        userInfo.middleName = personalInfo.getString("middleName");
        userInfo.lastName = personalInfo.getString("lastName");
      }
    } else {
      throw new BadRequestException("Missing fields");
    }
    return userInfo;
  }
}
