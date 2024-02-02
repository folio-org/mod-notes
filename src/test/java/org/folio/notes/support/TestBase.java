package org.folio.notes.support;

import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@DirtiesContext
@ContextConfiguration
@SpringBootTest
public abstract class TestBase {

  @Autowired
  protected FolioSpringLiquibase liquibase;
  @Autowired
  protected JdbcTemplate jdbc;

}
