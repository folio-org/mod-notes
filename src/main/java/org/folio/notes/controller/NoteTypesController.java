package org.folio.notes.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.rest.resource.NoteTypesApi;

public class NoteTypesController implements NoteTypesApi {

  @Override
  public ResponseEntity<NoteType> createNoteType(NoteType noteType) {
    return NoteTypesApi.super.createNoteType(noteType);
  }

  @Override
  public ResponseEntity<Void> deleteNoteType(UUID id) {
    return NoteTypesApi.super.deleteNoteType(id);
  }

  @Override
  public ResponseEntity<NoteType> getNoteType(UUID id) {
    return NoteTypesApi.super.getNoteType(id);
  }

  @Override
  public ResponseEntity<NoteTypeCollection> getNoteTypeCollection(String query, Integer offset, Integer limit) {
    return NoteTypesApi.super.getNoteTypeCollection(query, offset, limit);
  }

  @Override
  public ResponseEntity<Void> updateNoteType(UUID id, NoteType noteType) {
    return NoteTypesApi.super.updateNoteType(id, noteType);
  }
}
