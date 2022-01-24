package org.folio.notes.client;

import java.util.Optional;

import feign.codec.ErrorDecoder;
import feign.error.AnnotationErrorDecoder;
import feign.error.ErrorCodes;
import feign.error.ErrorHandling;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.folio.notes.domain.dto.User;
import org.folio.notes.exception.FolioExternalException;
import org.folio.notes.exception.FolioUnauthorizedException;
import org.folio.notes.exception.FolioUserNotFoundException;

@ErrorHandling(codeSpecific =
  {
    @ErrorCodes(codes = {401, 403}, generate = FolioUnauthorizedException.class)
  },
  defaultException = FolioExternalException.class
)
@FeignClient(value = "users", configuration = UsersClient.UsersClientConfig.class)
public interface UsersClient {

  @ErrorHandling(codeSpecific = {
    @ErrorCodes(codes = {404}, generate = FolioUserNotFoundException.class)
  })
  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  Optional<User> fetchUserById(@PathVariable("id") String id);

  @Configuration
  class UsersClientConfig {

    @Bean
    public ErrorDecoder usersClientErrorDecoder() {
      return AnnotationErrorDecoder.builderFor(UsersClient.class).build();
    }
  }
}
