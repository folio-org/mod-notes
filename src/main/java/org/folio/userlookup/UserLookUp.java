package org.folio.userlookup;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;

import org.folio.rest.RestVerticle;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

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
  public static CompletableFuture<UserLookUp> getUserInfo(final Map<String, String> okapiHeaders) {
    final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    final String userId = okapiHeaders.get(RestVerticle.OKAPI_USERID_HEADER);
    
    CompletableFuture<UserLookUp> future = new CompletableFuture<>();
    if (userId == null) {
      logger.error("No userid header");
      future.completeExceptionally(new IllegalArgumentException("Missing user id header, cannot look up user"));
      return future;
    }
    
    String okapiURL = okapiHeaders.get("X-Okapi-Url");
    String url = "/users/" + userId;
    try {
      final HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);
      future = httpClient.request(url, okapiHeaders)
        .thenApply(response -> {
          try {
            if (Response.isSuccess(response.getCode())) {
                return mapUserInfo(response);
            } else if (response.getCode() == 401) {
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
        });
    } catch (Exception e) {
      logger.error("Cannot get user data: " + e.getMessage(), e);
      future.completeExceptionally(e);
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
    }
    return userInfo;
  }
}
