package org.folio.notes;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@IntegrationTest
@EnablePostgres
@SpringBootTest
class ModNotesApplicationIT {

  @Autowired
  private ModNotesApplication modNotesApplication;

  @Test
  void contextLoads() {
    assertNotNull(modNotesApplication);
  }
}
