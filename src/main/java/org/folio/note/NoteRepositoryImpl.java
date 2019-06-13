package org.folio.note;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.NotFoundException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.db.CqlQuery;
import org.folio.db.model.NoteView;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;

@Component
public class NoteRepositoryImpl implements NoteRepository {

  private static final String NOTE_VIEW = "note_view";
  private static final String NOTE_TABLE = "note_data";

  private final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);

  @Autowired
  private Vertx vertx;

  @Override
  public Future<NoteCollection> findByQuery(String cqlQuery, int offset, int limit, String tenantId) {
    logger.debug("Getting notes. new query:" + cqlQuery);

    CqlQuery<NoteView> q = new CqlQuery<>(PostgresClient.getInstance(vertx, tenantId), NOTE_VIEW, NoteView.class);

    return q.get(cqlQuery, offset, limit).map(this::mapNoteResults);
  }

  /**
   * Saves a note record to the database
   *
   * @param note - current Note  {@link Note} object to save
   */
  @Override
  public Future<Note> save(Note note, String tenantId) {
    Future<String> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .save(NOTE_TABLE, note.getId(), note, future);

    return future.map(noteId -> {
      note.setId(noteId);
      return note;
    });
  }

  /**
   * Fetches a note record from the database
   *
   * @param id id of note to get
   */
  @Override
  public Future<Note> findOne(String id, String tenantId) {
    Future<NoteView> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .getById(NOTE_VIEW, id, NoteView.class, future);

    return future.map(noteView -> {
      if(Objects.isNull(noteView)){
        throw new NotFoundException("Note " + id + " not found");
      }
      return mapNoteView(noteView);
    });
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    Future<UpdateResult> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .delete(NOTE_TABLE, id, future);
    return future.map(updateResult -> {
      if(updateResult.getUpdated() == 0){
        throw new NotFoundException("Note with id " + id + " doesn't exist");
      }
      return null;
    });
  }

  @Override
  public Future<Void> update(String id, Note note, String tenantId) {
    Future<UpdateResult> future = Future.future();
    if (note.getLinks().isEmpty()) {
      PostgresClient.getInstance(vertx, tenantId)
        .delete(NOTE_TABLE, id, future);
    } else {
      PostgresClient.getInstance(vertx, tenantId)
        .update(NOTE_TABLE, note, id, future);
    }
    return future
      .map(updateResult -> {
        if(updateResult.getUpdated() == 0){
          throw new NotFoundException("Note with id " + id + " doesn't exist");
        }
        return null;
      });
  }

  private NoteCollection mapNoteResults(Results<NoteView> results) {
    List<Note> notes = results.getResults().stream()
      .map(this::mapNoteView)
      .collect(Collectors.toList());

    NoteCollection noteCollection = new NoteCollection();
    noteCollection.setNotes(notes);
    Integer totalRecords = results.getResultInfo().getTotalRecords();
    noteCollection.setTotalRecords(totalRecords);
    return noteCollection;
  }

  private Note mapNoteView(NoteView noteView) {
    return new Note()
      .withId(noteView.getId())
      .withTypeId(noteView.getTypeId())
      .withType(noteView.getType())
      .withDomain(noteView.getDomain())
      .withTitle(noteView.getTitle())
      .withContent(noteView.getContent())
      .withCreator(noteView.getCreator())
      .withUpdater(noteView.getUpdater())
      .withMetadata(noteView.getMetadata())
      .withLinks(noteView.getLinks());
  }
}
