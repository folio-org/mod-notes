package org.folio.notes.controller;

import javax.validation.Valid;

import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.folio.notes.service.TypeService;
import org.folio.spring.controller.TenantController;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;

@RestController("folioTenantController")
@RequestMapping(value = "/_/")
public class NoteTenantController extends TenantController {

  private final TypeService typeService;

  public NoteTenantController(TenantService tenantService, TypeService typeService) {
    super(tenantService);
    this.typeService = typeService;
  }

  @Override
  public ResponseEntity<String> postTenant(@Valid TenantAttributes tenantAttributes) {
    ResponseEntity<String> responseEntity = super.postTenant(tenantAttributes);
    if (responseEntity.getStatusCode().value() == HttpStatus.SC_OK) {
      typeService.populateDefaultType();
    }
    return responseEntity;
  }
}
