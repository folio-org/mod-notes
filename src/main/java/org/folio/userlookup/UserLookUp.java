package org.folio.userlookup;

import lombok.Builder;

/**
 * Retrieves user information from mod-users /users/{userId} endpoint.
 */
@Builder
public class UserLookUp {

  private final String userName;
  private final String firstName;
  private final String middleName;
  private final String lastName;

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
}
