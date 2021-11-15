package org.folio.notes.controller;

import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import org.folio.notes.domain.dto.NoteType;
import org.folio.notes.domain.dto.NoteTypeCollection;
import org.folio.notes.rest.resource.NoteTypesApi;
import org.folio.notes.sevice.TypeService;

@RestController
@RequiredArgsConstructor
public class NoteTypesController implements NoteTypesApi {

  private final TypeService typeService;

  @Override
  public ResponseEntity<NoteType> createNoteType(NoteType noteType) {
    NoteType newType = typeService.createType(noteType);
    return ResponseEntity.created(URI.create("/note-types/" + newType.getId())).body(newType);
  }

  @Override
  public ResponseEntity<Void> deleteNoteType(UUID id) {
    typeService.removeTypeById(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<NoteType> getNoteType(UUID id) {
    return ResponseEntity.ok(typeService.fetchById(id));
  }

  @Override
  public ResponseEntity<NoteTypeCollection> getNoteTypeCollection(String query, Integer offset, Integer limit) {
    return ResponseEntity.ok(typeService.fetchTypeCollection(query, offset, limit));
  }

  @Override
  public ResponseEntity<Void> updateNoteType(UUID id, NoteType noteType) {
    typeService.updateType(id, noteType);
    return ResponseEntity.noContent().build();
  }
}
