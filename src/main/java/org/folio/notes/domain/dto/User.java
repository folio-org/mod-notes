package org.folio.notes.domain.dto;

import java.util.UUID;

import lombok.Value;

@Value
public class User {

  UUID id;
  String username;
  UserPersonal personal;

  @Value
  public static class UserPersonal {

    String firstName;
    String lastName;
    String middleName;
  }
}
