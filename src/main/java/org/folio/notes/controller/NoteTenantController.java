package org.folio.notes.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.folio.notes.service.NoteTypesService;
import org.folio.spring.controller.TenantController;
import org.folio.spring.service.TenantService;

@RestController("folioTenantController")
@RequestMapping
public class NoteTenantController extends TenantController {

  private final NoteTypesService noteTypesService;

  public NoteTenantController(TenantService tenantService, NoteTypesService noteTypesService) {
    super(tenantService);
    this.noteTypesService = noteTypesService;
  }

  @Override
  protected void loadReferenceData() {
    super.loadReferenceData();
    noteTypesService.populateDefaultType();
  }
}
