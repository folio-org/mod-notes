package org.folio.notes.service.impl;

import static org.folio.notes.config.CacheConfig.CACHE_USERS_BY_ID;

import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.folio.notes.client.UsersClient;
import org.folio.notes.domain.dto.User;
import org.folio.notes.service.UsersService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersServiceImpl implements UsersService {

  private final UsersClient client;

  @Override
  @Cacheable(cacheNames = CACHE_USERS_BY_ID, key = "@folioExecutionContext.tenantId + ':' + #id",
             unless = "#result == null")
  public Optional<User> getUser(UUID id) {
    log.debug("getUser:: trying to get user with id: {}", id);
    try {
      return id == null ? Optional.empty() : client.fetchUserById(id.toString());
    } catch (FeignException e) {
      log.warn("getUser:: error while getting user with id: {}: {}", id, e.getMessage());
      return Optional.empty();
    }
  }
}
