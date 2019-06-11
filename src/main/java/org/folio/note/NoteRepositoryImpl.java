package org.folio.note;

import static io.vertx.core.Future.failedFuture;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.NotFoundException;

import org.folio.db.DbUtils;
import org.folio.db.model.NoteView;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;

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
    Future<NoteCollection> future = Future.succeededFuture(null);
    return future.compose(o -> {
      CQLWrapper cql;
      try {
        cql = DbUtils.getCQLWrapper(NOTE_VIEW, cqlQuery, limit, offset);
      } catch (CQL2PgJSONException e) {
        return failedFuture(new IllegalArgumentException("Failed to parse cql query", e));
      }
      return getNotes(tenantId, cql);
    });
  }

  /**
   * Saves a note record to the database
   *
   * @param note - current Note  {@link Note} object to save
   */
  @Override
  public Future<Note> save(Note note, String tenantId) {
    initId(note);
    Future<String> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .save(NOTE_TABLE, note.getId(), note, future.completer());

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
      .getById(NOTE_VIEW, id, NoteView.class, future.completer());

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
      .delete(NOTE_TABLE, id, future.completer());
    return future.map(updateResult -> {
      if(updateResult.getUpdated() == 0){
        throw new NotFoundException("Note with id " + id + " doesn't exist");
      }
      return null;
    });
  }

  @Override
  public Future<Void> save(String id, Note note, String tenantId) {
    Future<UpdateResult> future = Future.future();
    if (note.getLinks().isEmpty()) {
      PostgresClient.getInstance(vertx, tenantId)
        .delete(NOTE_TABLE, id, future.completer());
    } else {
      PostgresClient.getInstance(vertx, tenantId)
        .update(NOTE_TABLE, note, id, future.completer());
    }
    return future
      .map(updateResult -> {
        if(updateResult.getUpdated() == 0){
          throw new NotFoundException("Note with id " + id + " doesn't exist");
        }
        return null;
      });
  }

  private Future<NoteCollection> getNotes(String tenantId, CQLWrapper cql) {
    Future<Results<NoteView>> future = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
      .get(NOTE_VIEW, NoteView.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        future.completer());

    return future
      .map(this::mapNoteResults);
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

  /**
   * Sets a note record random UUID
   *
   * @param note - current Note {@link Note} object
   */
  private void initId(Note note) {
    String noteId = note.getId();
    if (noteId == null || noteId.isEmpty()) {
      note.setId(UUID.randomUUID().toString());
    }
  }

}
