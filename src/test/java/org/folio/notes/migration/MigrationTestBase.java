package org.folio.notes.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.notes.support.TestBase;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;

@IntegrationTest
@EnablePostgres
abstract class MigrationTestBase extends TestBase {

  protected void assertTableExist(String tableName) {
    assertTableExistence(tableName, Boolean.TRUE);
  }

  protected void assertTableNotExist(String tableName) {
    assertTableExistence(tableName, Boolean.FALSE);
  }

  protected void assertViewExist(String tableName) {
    assertViewExistence(tableName, Boolean.TRUE);
  }

  protected void assertViewNotExist(String tableName) {
    assertViewExistence(tableName, Boolean.FALSE);
  }

  protected void assertTriggerExist(String tableName) {
    assertTriggerExistence(tableName, Boolean.TRUE);
  }

  protected void assertTriggerNotExist(String tableName) {
    assertTriggerExistence(tableName, Boolean.FALSE);
  }

  protected void assertRowsCount(String tableName, int expectedCount) {
    var actualCount = jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
    assertEquals(expectedCount, actualCount);
  }

  private void assertTableExistence(String tableName, Boolean expectedExistence) {
    String query =
      "select count(*) > 0 "
        + "from information_schema.tables "
        + "where table_name = ?";
    Boolean exist = jdbc.queryForObject(query, Boolean.class, tableName);
    assertEquals(expectedExistence, exist);
  }

  private void assertViewExistence(String viewName, Boolean expectedExistence) {
    String query =
      "select count(*) > 0 "
        + "from information_schema.views "
        + "where table_name = ?";
    Boolean exist = jdbc.queryForObject(query, Boolean.class, viewName);
    assertEquals(expectedExistence, exist);
  }

  private void assertTriggerExistence(String triggerName, Boolean expectedExistence) {
    String query =
      "select count(*) > 0 "
        + "from information_schema.triggers "
        + "where trigger_name = ?";
    Boolean exist = jdbc.queryForObject(query, Boolean.class, triggerName);
    assertEquals(expectedExistence, exist);
  }
}
