package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_OK;
import static org.folio.rest.impl.NoteTenantApiImpl.DEFAULT_NOTE_TYPE_NAME;
import static org.hamcrest.Matchers.is;

import org.folio.rest.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class NoteTenantApiImplTest extends TestBase {

  private static final String NOTE_TYPES_ENDPOINT = "/note-types";

  @Test
  public void shouldCreateDefaultNoteTypeWhenTenantCreated() {
    try {
      getWithValidateBody(NOTE_TYPES_ENDPOINT, SC_OK)
        .body("noteTypes[0].name", is(DEFAULT_NOTE_TYPE_NAME));
    } finally {
      DBTestUtil.deleteAllNoteTypes(vertx);
    }
  }
}
