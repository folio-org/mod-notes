package org.folio.note;

import static io.vertx.core.Future.failedFuture;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.folio.common.OkapiParams;
import org.folio.rest.RestVerticle;
import org.folio.rest.exceptions.InputValidationException;
import org.folio.rest.jaxrs.model.Link;
import org.folio.rest.jaxrs.model.Note;
import org.folio.rest.jaxrs.model.NoteCollection;
import org.folio.rest.jaxrs.model.UserDisplayInfo;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.userlookup.UserLookUp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;

@Component
public class NoteServiceImpl implements NoteService {

  private static final String NOTE_TABLE = "note_data";

  private NoteRepository repository;

  @Autowired
  public NoteServiceImpl(NoteRepository repository) {
    this.repository = repository;
  }

  @Override
  public Future<NoteCollection> getNotes(String cqlQuery, int offset, int limit, String tenantId, Vertx vertx) {
    return repository.getNotes(cqlQuery, offset, limit, tenantId, vertx);
  }

  @Override
  public Future<Note> addNote(Note note, UserLookUp creatorUser, Vertx vertx, OkapiParams okapiParams) {
    final List<Link> links = note.getLinks();
    if (Objects.isNull(links) || links.isEmpty()) {
      throw new InputValidationException("links", "links", "At least one link should be present");
    }
    note.setCreator(getUserDisplayInfo(creatorUser.getFirstName(), creatorUser.getMiddleName(), creatorUser.getLastName()));
    note.getMetadata().setCreatedByUsername(creatorUser.getUserName());
    return repository.saveNote(note, vertx, okapiParams.getTenant());
  }

  /**
   * Fetches a note record from the database
   *
   * @param id id of note to get
   */
  @Override
  public Future<Note> getOneNote(String id, String tenantId, Vertx vertx) {
    return repository.getOneNote(id, tenantId, vertx);
  }

  @Override
  public Future<Void> deleteNote(String id, Handler<AsyncResult<Response>> asyncResultHandler, String tenantId, Vertx vertx) {
    return repository.deleteNote(id, tenantId, vertx);
  }

  @Override
  public Future<Void> updateNoteWithUser(String id, Note note, Map<String, String> okapiHeaders, Context vertxContext) {
    return setNoteUpdater(note, UserLookUp.getUserInfo(okapiHeaders))
      .compose(voidObject -> updateNote(id, note, TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT)), vertxContext.owner()));
  }

  private Future<Void> setNoteUpdater(Note note, Future<UserLookUp> userInfo) {
    return userInfo
      .map(userLookUp -> {
        final UserDisplayInfo userDisplayInfo = getUserDisplayInfo(userLookUp.getFirstName(), userLookUp.getMiddleName(), userLookUp.getLastName());
        note.setUpdater(userDisplayInfo);
        note.getMetadata().setUpdatedByUsername(userLookUp.getUserName());
        return null;
      });
  }

  private Future<Void> updateNote(String id, Note note, String tenantId, Vertx vertx) {
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
          throw new NotFoundException();
        }
        return (Void) null;
      })
      .recover(throwable -> {
        String badRequestMessage = PgExceptionUtil.badRequestMessage(throwable);
        if (badRequestMessage != null) {
          return failedFuture(new BadRequestException(badRequestMessage, throwable));
        } else {
          return failedFuture(throwable);
        }
      });
  }

  private UserDisplayInfo getUserDisplayInfo(String firstName, String middleName, String lastName) {
    final UserDisplayInfo userDisplayInfo = new UserDisplayInfo();
    userDisplayInfo.setFirstName(firstName);
    userDisplayInfo.setMiddleName(middleName);
    userDisplayInfo.setLastName(lastName);
    return userDisplayInfo;
  }
}
