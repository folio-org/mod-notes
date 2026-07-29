package org.folio.notes.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.folio.notes.service.NoteTypesService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
@Slf4j
public class NoteTenantService extends TenantService {

  private final NoteTypesService noteTypesService;
  private final KafkaAdminService kafkaAdminService;

  public NoteTenantService(JdbcTemplate jdbcTemplate,
                           FolioExecutionContext context,
                           FolioSpringLiquibase folioSpringLiquibase,
                           NoteTypesService noteTypesService,
                           KafkaAdminService kafkaAdminService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.noteTypesService = noteTypesService;
    this.kafkaAdminService = kafkaAdminService;
  }

  @Override
  public void loadReferenceData() {
    noteTypesService.populateDefaultType();
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    super.afterTenantUpdate(tenantAttributes);
    kafkaAdminService.createTopics(context.getTenantId());
  }
}
