package org.folio.notes.migration;

import liquibase.exception.LiquibaseException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

public class PreviousDatabaseWithDataMigrationTest extends MigrationTestBase {

  @Sql(
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    scripts = {"/migration/schema-v2-13.sql", "/migration/schema-v2-13-data.sql"},
    config = @SqlConfig(separator = ScriptUtils.EOF_STATEMENT_SEPARATOR)
  )
  @Test
  void testMigrationOnPreviousVersionDatabaseWithData() throws LiquibaseException {
    liquibase.performLiquibaseUpdate();
    assertTableNotExist("rmb_job");
    assertTableNotExist("rmb_internal");
    assertTableNotExist("rmb_internal_analyze");
    assertTableNotExist("rmb_internal_index");
    assertTableNotExist("note_data");
    assertTableNotExist("note_type");
    assertTableExist("note");
    assertTableExist("link");
    assertTableExist("note_link");
    assertTableExist("type");
    assertViewNotExist("note_type_view");
    assertViewNotExist("note_view");
    assertTriggerNotExist("set_id_in_jsonb");
    assertTriggerNotExist("update_search_content");
    assertTriggerNotExist("update_type_id");
    assertTriggerNotExist("set_note_data_md_json_trigger");
    assertTriggerNotExist("set_note_type_md_json_trigger");
    assertRowsCount("type", 2);
    assertRowsCount("note", 3);
    assertRowsCount("link", 3);
    assertRowsCount("note_link", 4);
  }

}
