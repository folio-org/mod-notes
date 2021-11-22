package org.folio.notes.controller;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.folio.notes.domain.dto.LinkStatusFilter;
import org.folio.notes.domain.dto.Note;
import org.folio.notes.domain.dto.NoteCollection;
import org.folio.notes.domain.dto.NoteLinkUpdateCollection;
import org.folio.notes.domain.dto.NotesOrderBy;
import org.folio.notes.domain.dto.OrderDirection;
import org.folio.notes.rest.resource.NotesApi;
import org.folio.notes.service.NotesService;

@RestController
@RequiredArgsConstructor
public class NotesController implements NotesApi {

  private final NotesService notesService;

  @Override
  public ResponseEntity<Note> createNote(Note note) {
    var newNote = notesService.createNote(note);
    var location = ServletUriComponentsBuilder
      .fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(newNote.getId())
      .toUri();
    return ResponseEntity.created(location).body(newNote);
  }

  @Override
  public ResponseEntity<Void> deleteNote(UUID id) {
    notesService.deleteNote(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Note> getNote(UUID id) {
    return ResponseEntity.ok(notesService.getNote(id));
  }

  @Override
  public ResponseEntity<NoteCollection> getNoteCollection(String query, Integer offset, Integer limit) {
    return ResponseEntity.ok(notesService.getNoteCollection(query, offset, limit));
  }

  @Override
  public ResponseEntity<NoteCollection> getNoteCollectionByLink(String domain, String objectType, String objectId,
                                                                String search, List<String> noteType,
                                                                LinkStatusFilter status, NotesOrderBy orderBy,
                                                                OrderDirection order, Integer offset, Integer limit) {
    return ResponseEntity.ok(notesService.getNoteCollection(domain, objectType, objectId, search, noteType, status,
      orderBy, order, offset, limit));
  }

  @Override
  public ResponseEntity<Void> updateLinks(String objectType, String objectId,
                                          NoteLinkUpdateCollection noteLinkUpdateCollection) {
    notesService.updateLinks(objectType, objectId, noteLinkUpdateCollection);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateNote(UUID id, Note note) {
    notesService.updateNote(id, note);
    return ResponseEntity.noContent().build();
  }
}
