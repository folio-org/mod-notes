package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;

import org.folio.rest.TestBase;
import org.folio.spring.SpringContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class NoteTenantApiImplTest extends TestBase {

  private static final String NOTE_TYPES_ENDPOINT = "/note-types";
  @Value("${note.types.default.name}")
  private String defaultNoteTypeName;

  @Before
  public void setUp() {
    SpringContextUtil.autowireDependenciesFromFirstContext(this, vertx);
  }

  @Test
  public void shouldCreateDefaultNoteTypeWhenTenantCreated() {
    try {
      getWithValidateBody(NOTE_TYPES_ENDPOINT, SC_OK)
        .body("noteTypes[0].name", is(defaultNoteTypeName));
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }
}
