package org.folio.note;

import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Context;
import io.vertx.core.Future;

public interface NoteService {
  Future<NoteCollection> getNotes(Context vertxContext, String tenantId, CQLWrapper cql);

  Future<NoteCollection> getNotes(String query, int offset, int limit, Context vertxContext, String tenantId);
}
