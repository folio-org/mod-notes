package org.folio.notes.support;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.FolioModuleMetadata;

public class DatabaseHelper {

  private final FolioModuleMetadata metadata;
  private final JdbcTemplate jdbcTemplate;

  public DatabaseHelper(FolioModuleMetadata metadata, JdbcTemplate jdbcTemplate) {
    this.metadata = metadata;
    this.jdbcTemplate = jdbcTemplate;
  }

  public int countRowsInTable(String tenant) {
    return JdbcTestUtils.countRowsInTable(jdbcTemplate, getNoteTypeTable(tenant));
  }

  public String getNoteTypeTable(String tenantId) {
    return metadata.getDBSchemaName(tenantId) + "." + "type";
  }

  public void clearTable(String tenant) {
    JdbcTestUtils.deleteFromTables(jdbcTemplate, getNoteTypeTable(tenant));
  }

  public void saveNoteType(NoteTypeEntity noteType, String tenant) {
    var sql = "INSERT INTO " + getNoteTypeTable(tenant) + " (id, name) VALUES (?, ?)";
    jdbcTemplate.update(sql, noteType.getId(), noteType.getName());
  }

  public void saveNoteTypes(List<NoteTypeEntity> noteTypes, String tenant) {
    var sql = "INSERT INTO " + getNoteTypeTable(tenant) + " (name) VALUES (?)";
    var args = noteTypes.stream()
      .map(noteType -> new Object[] {noteType.getName()})
      .collect(Collectors.toList());
    jdbcTemplate.batchUpdate(sql, args);
  }
}

