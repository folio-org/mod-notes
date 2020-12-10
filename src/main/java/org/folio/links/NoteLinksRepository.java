package org.folio.links;

import java.util.List;

import io.vertx.core.Future;

import org.folio.model.EntityLink;
import org.folio.model.Order;
import org.folio.model.OrderBy;
import org.folio.model.RowPortion;
import org.folio.model.Status;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;

public interface NoteLinksRepository {

  Future<Void> update(Link link, List<String> assignNotes, List<String> unAssignNotes, String tenantId);

  Future<NoteCollection> findNotesByTitleAndNoteTypeAndStatus(EntityLink link, String search, List<String> noteTypes,
                                                              Status status, OrderBy orderBy, Order order,
                                                              RowPortion rowPortion, String tenantId);

  Future<Integer> countNotesByTitleAndNoteTypeAndStatus(EntityLink link, String search, List<String> noteTypes, Status status,
                                                        String tenantId);
}
