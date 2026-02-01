package org.folio.notes.client;

import java.util.Optional;
import org.folio.notes.domain.dto.User;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "users", contentType = MediaType.APPLICATION_JSON_VALUE)
public interface UsersClient {

  @GetExchange(value = "/{id}", accept = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
  Optional<User> fetchUserById(@PathVariable String id);
}
