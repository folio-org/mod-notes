package org.folio.links;

import io.vertx.core.Future;
import org.apache.commons.lang3.mutable.MutableObject;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.NoteLinkPut;
import org.folio.rest.jaxrs.model.NoteLinksPut;
import org.folio.rest.model.Order;
import org.folio.rest.model.OrderBy;
import org.folio.rest.model.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class NoteLinksServiceImpl implements NoteLinksService {

  @Autowired
  private NoteLinksRepository noteLinksRepository;

  @Override
  public Future<Void> updateNoteLinks(NoteLinksPut entity, Link link, String tenantId) {
    List<String> assignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.ASSIGNED);
    List<String> unAssignNotes = getNoteIdsByStatus(entity, NoteLinkPut.Status.UNASSIGNED);

    return noteLinksRepository.updateNoteLinks(link, assignNotes, unAssignNotes, tenantId);
  }

  @Override
  public Future<NoteCollection> findByQueryNotes(Status parsedStatus, Order parsedOrder,
                                                 OrderBy parsedOrderBy, String domain, String title, Link link, int limit, int offset, String tenantId) {
    MutableObject<Integer> mutableTotalRecords = new MutableObject<>();
    return noteLinksRepository.countNotes(parsedStatus, domain, title, link, tenantId)
      .compose(count -> {
        mutableTotalRecords.setValue(count);
        return noteLinksRepository.findByQueryNotes(parsedStatus, parsedOrder, parsedOrderBy,
          domain, title, link, limit, offset, tenantId);
      })
      .map(notes -> {
        notes.setTotalRecords(mutableTotalRecords.getValue());
        return notes;
      });
  }

  private List<String> getNoteIdsByStatus(NoteLinksPut entity, NoteLinkPut.Status status) {
    return entity.getNotes().stream()
      .filter(note -> status.equals(note.getStatus()))
      .map(NoteLinkPut::getId)
      .collect(Collectors.toList());
  }
}
