package org.folio.notes.migration;

import liquibase.exception.LiquibaseException;
import org.junit.jupiter.api.Test;

class EmptyDatabaseMigrationIT extends MigrationTestBase {

  @Test
  void testMigrationOnEmptyDatabase() throws LiquibaseException {
    liquibase.performLiquibaseUpdate();
    assertTableExist("note");
    assertTableExist("link");
    assertTableExist("note_link");
    assertTableExist("type");
    assertRowsCount("type", 1);
  }

}
