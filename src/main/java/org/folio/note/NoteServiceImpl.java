package org.folio.note;

import static io.vertx.core.Future.failedFuture;

import java.util.List;
import java.util.Objects;

import org.folio.common.OkapiParams;
import org.folio.rest.exceptions.InputValidationException;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.userlookup.UserLookUpService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@Component
public class NoteServiceImpl implements NoteService {

  private final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);

  @Autowired
  private NoteRepository repository;
  @Autowired
  private UserLookUpService userLookUpService;

  @Override
  public Future<NoteCollection> getNotes(String cqlQuery, int offset, int limit, String tenantId) {
    return repository.findByQuery(cqlQuery, offset, limit, tenantId);
  }

  @Override
  public Future<Note> addNote(Note note, OkapiParams okapiParams) {
    logger.debug("Removing unsafe tags");
    note.setContent(sanitizeHtml(note.getContent()));
    logger.debug("Create note with content: {}", Json.encode(note));
    final List<Link> links = note.getLinks();
    if (Objects.isNull(links) || links.isEmpty()) {
      return failedFuture(new InputValidationException("links", "links", "At least one link should be present"));
    }

    return userLookUpService.getUserInfo(okapiParams.getHeadersAsMap())
      .compose(creatorUser -> {
        note.setCreator(getUserDisplayInfo(creatorUser.getFirstName(), creatorUser.getMiddleName(), creatorUser.getLastName()));
        note.getMetadata().setCreatedByUsername(creatorUser.getUserName());
        return repository.save(note, okapiParams.getTenant());
      });
  }

  /**
   * Fetches a note record from the database
   *
   * @param id id of note to get
   */
  @Override
  public Future<Note> getOneNote(String id, String tenantId) {
    return repository.findOne(id, tenantId);
  }

  @Override
  public Future<Void> deleteNote(String id, String tenantId) {
    return repository.delete(id, tenantId);
  }

  @Override
  public Future<Void> updateNote(String id, Note note, OkapiParams okapiParams) {
    logger.debug("Removing unsafe tags");
    note.setContent(sanitizeHtml(note.getContent()));
    logger.debug("PUT note with id:{} and content: {}", id, Json.encode(note));
    if (note.getId() == null) {
      note.setId(id);
      logger.debug("No Id in the note, taking the one from the link");
      // The RMB should handle this. See RMB-94
    }
    if (!note.getId().equals(id)) {
      return failedFuture(new InputValidationException("id", note.getId(), "Can not change Id"));
    }


    return userLookUpService.getUserInfo(okapiParams.getHeadersAsMap())
      .compose(userLookUp -> {
        final UserDisplayInfo userDisplayInfo = getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName());
        note.setUpdater(userDisplayInfo);
        note.getMetadata().setUpdatedByUsername(userLookUp.getUserName());
        return repository.update(id, note, okapiParams.getTenant());
      });
  }

  private UserDisplayInfo getUserDisplayInfo(String firstName, String middleName, String lastName) {
    final UserDisplayInfo userDisplayInfo = new UserDisplayInfo();
    userDisplayInfo.setFirstName(firstName);
    userDisplayInfo.setMiddleName(middleName);
    userDisplayInfo.setLastName(lastName);
    return userDisplayInfo;
  }

  private String sanitizeHtml(String content) {
    return Jsoup.clean(content, Whitelist.relaxed().removeTags("img"));
  }
}
