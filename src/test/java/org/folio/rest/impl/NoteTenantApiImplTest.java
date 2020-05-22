package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;

import static org.folio.rest.NotesTestBase.NOTE_TYPE_TABLE;
import static org.folio.test.util.DBTestUtil.deleteFromTable;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;

import org.folio.spring.SpringContextUtil;
import org.folio.test.util.TestBase;

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
      deleteFromTable(vertx, NOTE_TYPE_TABLE);
    }
  }
}
