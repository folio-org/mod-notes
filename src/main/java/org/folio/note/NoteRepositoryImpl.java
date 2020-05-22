package org.folio.note;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.NotFoundException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.db.CqlQuery;
import org.folio.model.NoteView;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.interfaces.Results;

@Component
public class NoteRepositoryImpl implements NoteRepository {

  private static final String NOTE_VIEW = "note_view";
  private static final String NOTE_TABLE = "note_data";

  private final Logger logger = LoggerFactory.getLogger(NoteRepositoryImpl.class);

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
    Promise<String> promise = Promise.promise();

    if (StringUtils.isBlank(note.getId())) {
      note.setId(UUID.randomUUID().toString());
    }

    PostgresClient.getInstance(vertx, tenantId)
      .save(NOTE_TABLE, note.getId(), note, promise);

    return promise.future().map(noteId -> {
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
    Promise<NoteView> promise = Promise.promise();
    PostgresClient.getInstance(vertx, tenantId)
      .getById(NOTE_VIEW, id, NoteView.class, promise);

    return promise.future().map(noteView -> {
      if (Objects.isNull(noteView)) {
        throw new NotFoundException("Note " + id + " not found");
      }
      return mapNoteView(noteView);
    });
  }

  @Override
  public Future<Void> delete(String id, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    PostgresClient.getInstance(vertx, tenantId)
      .delete(NOTE_TABLE, id, promise);
    return promise.future().map(updateResult -> {
      if (updateResult.rowCount() == 0) {
        throw new NotFoundException("Note with id " + id + " doesn't exist");
      }
      return null;
    });
  }

  @Override
  public Future<Void> update(String id, Note note, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    if (note.getLinks().isEmpty()) {
      PostgresClient.getInstance(vertx, tenantId)
        .delete(NOTE_TABLE, id, promise);
    } else {
      PostgresClient.getInstance(vertx, tenantId)
        .update(NOTE_TABLE, note, id, promise);
    }
    return promise
      .future()
      .map(updateResult -> {
        if (updateResult.rowCount() == 0) {
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
