package org.folio.notes.domain.dto;

import java.util.UUID;

public record User(UUID id, String username, org.folio.notes.domain.dto.User.UserPersonal personal) {

  public record UserPersonal(String firstName, String lastName, String middleName) { }
}
