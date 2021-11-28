package org.folio.notes.support;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import org.folio.notes.domain.entity.NoteEntity;
import org.folio.notes.domain.entity.NoteTypeEntity;
import org.folio.spring.FolioModuleMetadata;

public class DatabaseHelper {

  private static final String NOTE = "note";
  private static final String TYPE = "type";
  private final FolioModuleMetadata metadata;
  private final JdbcTemplate jdbcTemplate;

  public DatabaseHelper(FolioModuleMetadata metadata, JdbcTemplate jdbcTemplate) {
    this.metadata = metadata;
    this.jdbcTemplate = jdbcTemplate;
  }

  public int countRowsInTable(String tenant, String table) {
    return JdbcTestUtils.countRowsInTable(jdbcTemplate, getTable(tenant, table));
  }

  public String getTable(String tenantId, String table) {
    return metadata.getDBSchemaName(tenantId) + "." + table;
  }

  public void clearTable(String tenant) {
    JdbcTestUtils.deleteFromTables(jdbcTemplate, getTable(tenant, TYPE));
    JdbcTestUtils.deleteFromTables(jdbcTemplate, getTable(tenant, TYPE));
  }

  public void saveNoteType(NoteTypeEntity noteType, String tenant) {
    var sql = "INSERT INTO " + getTable(tenant, TYPE) + " (id, name) VALUES (?, ?)";
    jdbcTemplate.update(sql, noteType.getId(), noteType.getName());
  }

  public void saveNote(NoteEntity note, String tenant) {
    var sql = "INSERT INTO " + getTable(tenant, NOTE) + " (id, title, domain, type_id) VALUES (?,?,?,?)";
    jdbcTemplate.update(sql, note.getId(), note.getTitle(), note.getDomain(), note.getType().getId());
  }

  public void saveNoteTypes(List<NoteTypeEntity> noteTypes, String tenant) {
    var sql = "INSERT INTO " + getTable(tenant, TYPE) + " (name) VALUES (?)";
    var args = noteTypes.stream()
      .map(noteType -> new Object[] {noteType.getName()})
      .collect(Collectors.toList());
    jdbcTemplate.batchUpdate(sql, args);
  }

  public void saveNotes(List<NoteEntity> noteTypes, String tenant) {
    var sql = "INSERT INTO " + getTable(tenant, NOTE) + " (title, domain, type_id) VALUES (?,?,?)";
    var args = noteTypes.stream()
      .map(noteType -> new Object[] {noteType.getTitle(), noteType.getDomain(), noteType.getType().getId()})
      .collect(Collectors.toList());
    jdbcTemplate.batchUpdate(sql, args);
  }
}

