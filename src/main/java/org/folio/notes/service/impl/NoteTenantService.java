package org.folio.notes.service.impl;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import org.folio.notes.service.NoteTypesService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;

@Service
@Primary
public class NoteTenantService extends TenantService {

  private final NoteTypesService noteTypesService;

  public NoteTenantService(JdbcTemplate jdbcTemplate,
                           FolioExecutionContext context,
                           FolioSpringLiquibase folioSpringLiquibase,
                           NoteTypesService noteTypesService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.noteTypesService = noteTypesService;
  }

  @Override
  public void loadReferenceData() {
    super.loadReferenceData();
    noteTypesService.populateDefaultType();
  }
}

