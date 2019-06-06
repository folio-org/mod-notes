package org.folio.note;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.db.model.NoteView;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.stereotype.Component;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@Component
public class NoteServiceImpl implements NoteService {
  private static final String NOTE_VIEW = "note_view";
  private static final String NOTE_TABLE = "note_data";
  private static final String LOCATION_PREFIX = "/notes/";

  private final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);

  @Override
  public Future<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql) {
    Future<Results<NoteView>> future = Future.future();
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(NOTE_VIEW, NoteView.class, new String[]{"*"}, cql,
        true /*get count too*/, false /* set id */,
        future.completer());

    return future
      .map(this::mapNoteResults);
  }

  @Override
  public Future<NoteCollection> getNotes(String query, int offset, int limit, Context vertxContext, String tenantId) {
    logger.debug("Getting notes. new query:" + query);
    Future<NoteCollection> future = Future.succeededFuture(null);
    return future.compose(o -> {
      CQLWrapper cql;
      try {
        cql = getCQLForNoteView(query, limit, offset);
      } catch (CQL2PgJSONException e) {
        throw new IllegalArgumentException("Failed to parse cql query");
      }
      return getNotes(vertxContext, tenantId, cql);
    });
  }

  private CQLWrapper getCQLForNoteView(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(NOTE_VIEW + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
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
