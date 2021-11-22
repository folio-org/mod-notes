package org.folio.notes.service;

import java.util.Optional;
import java.util.UUID;

import org.folio.notes.domain.dto.User;

public interface UsersService {

  Optional<User> getUser(UUID id);
}
