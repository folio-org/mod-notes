package org.folio.notes.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.folio.notes.service.impl.NoteTenantService;

@ExtendWith(MockitoExtension.class)
class NoteTenantServiceTest {

  @Mock
  private NoteTypesService noteTypesService;

  @InjectMocks
  private NoteTenantService tenantService;

  @Test
  void shouldPopulateDefaultTypeOnLoadReferenceData() {
    tenantService.loadReferenceData();
    verify(noteTypesService).populateDefaultType();
  }

}
