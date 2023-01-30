package org.folio.notes.client;

import java.util.Optional;
import org.folio.notes.domain.dto.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "users", dismiss404 = true)
public interface UsersClient {

  @GetMapping(value = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
  Optional<User> fetchUserById(@PathVariable("id") String id);

}
