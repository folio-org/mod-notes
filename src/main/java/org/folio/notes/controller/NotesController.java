package org.folio.notes.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.dto.NoteLinkCollection;
import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.rest.resource.NotesApi;

public class NotesController implements NotesApi {

  @Override
  public ResponseEntity<Void> assignLinkToNotes(String objectType, String objectId, NoteLinkCollection noteLinkCollection) {
    return NotesApi.super.assignLinkToNotes(objectType, objectId, noteLinkCollection);
  }

  @Override
  public ResponseEntity<NoteType> createNote(Note note) {
    return NotesApi.super.createNote(note);
  }

  @Override
  public ResponseEntity<Void> deleteNote(UUID id) {
    return NotesApi.super.deleteNote(id);
  }

  @Override
  public ResponseEntity<Note> getNote(UUID id) {
    return NotesApi.super.getNote(id);
  }

  @Override
  public ResponseEntity<NoteCollection> getNoteCollection(String query, Integer offset, Integer limit) {
    return NotesApi.super.getNoteCollection(query, offset, limit);
  }

  @Override
  public ResponseEntity<NoteCollection> getNoteCollectionByLink(String domain, String objectType, String objectId,
                                                                String search, List<String> noteType, String status,
                                                                String orderBy, String order, Integer offset,
                                                                Integer limit) {
    return NotesApi.super.getNoteCollectionByLink(domain, objectType, objectId, search, noteType, status, orderBy, order,
      offset, limit);
  }

  @Override
  public ResponseEntity<Void> updateNote(UUID id, Note note) {
    return NotesApi.super.updateNote(id, note);
  }
}
