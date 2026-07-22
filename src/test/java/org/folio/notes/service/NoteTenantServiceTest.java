package org.folio.notes.service;

import static org.mockito.Mockito.verify;

import org.folio.notes.service.impl.NoteTenantService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.KafkaAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class NoteTenantServiceTest {

  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private FolioSpringLiquibase folioSpringLiquibase;
  @Mock
  private NoteTypesService noteTypesService;
  @Mock
  private KafkaAdminService kafkaAdminService;

  @InjectMocks
  private NoteTenantService tenantService;

  @Test
  void shouldPopulateDefaultTypeOnLoadReferenceData() {
    tenantService.loadReferenceData();
    verify(noteTypesService).populateDefaultType();
  }
}
