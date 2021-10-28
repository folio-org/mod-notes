package org.folio.notes.config;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.folio.spring.FolioExecutionContext;

@Configuration
@EnableJpaAuditing(modifyOnCreate = false)
public class JpaConfig {


  @Component
  @RequiredArgsConstructor
  public static class FolioAuditorAware implements AuditorAware<UUID> {

    private final FolioExecutionContext folioExecutionContext;

    @NonNull
    @Override
    public Optional<UUID> getCurrentAuditor() {
      return Optional.ofNullable(folioExecutionContext.getUserId());
    }
  }
}
