package org.folio.rest.exc;

import static org.folio.util.pf.PartialFunctions.pf;

import java.util.function.Predicate;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSONException;

import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.util.pf.PartialFunction;
import org.folio.util.pf.PartialFunctions;

public class ExceptionHandlers {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlers.class);

  private ExceptionHandlers() {
  }

  @SuppressWarnings("squid:CommentedOutCodeLine")
  public static PartialFunction<Throwable, Response> badRequestHandler() {
    // predicate can be written also as:
    //    t -> t instanceof BadRequestException || t instanceof CQL2PgJSONException
    //
    // the below is to show how predicates that potentially have complex logic can be combined
    return pf(instanceOf(BadRequestException.class)
                .or(instanceOf(GenericDatabaseException.class).and(invalidUUID()))
                .or(instanceOf(CQLQueryValidationException.class))
                .or(instanceOf(CQL2PgJSONException.class)),
              ExceptionHandlers::toBadRequest);
  }

  public static PartialFunction<Throwable, Response> notFoundHandler() {
    return pf(NotFoundException.class::isInstance, ExceptionHandlers::toNotFound);
  }

  public static PartialFunction<Throwable, Response> generalHandler() {
    return pf(t -> true, ExceptionHandlers::toGeneral);
  }

  public static PartialFunction<Throwable, Response> logged(PartialFunction<Throwable, Response> pf) {
    return PartialFunctions.logged(pf, t -> LOGGER.error("Execution failed with: " + t.getMessage(), t));
  }

  private static Response status(int status, String msg) {
    return Response.status(status)
      .type(MediaType.TEXT_PLAIN)
      .entity(StringUtils.defaultString(msg))
      .build();
  }

  private static Response toBadRequest(Throwable t) {
    return status(HttpStatus.SC_BAD_REQUEST, t.getMessage());
  }

  private static Response toNotFound(Throwable t) {
    return status(HttpStatus.SC_NOT_FOUND, t.getMessage());
  }

  private static Response toGeneral(Throwable t) {
    Future<Response> validationFuture = Future.future();
    ValidationHelper.handleError(t, validationFuture);

    if (validationFuture.succeeded()) {
      return validationFuture.result();
    } else {
      return status(HttpStatus.SC_INTERNAL_SERVER_ERROR, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
  }

  private static Predicate<Throwable> instanceOf(Class<? extends Throwable> cl) {
    return new InstanceOfPredicate<>(cl);
  }

  private static Predicate<Throwable> invalidUUID() {
    return t -> ValidationHelper.isInvalidUUID(t.getMessage());
  }

  private static class InstanceOfPredicate<T, E> implements Predicate<E> {

    private Class<T> excClass;

    InstanceOfPredicate(Class<T> excClass) {
      this.excClass = excClass;
    }

    @Override
    public boolean test(E e) {
      return excClass.isInstance(e);
    }
  }

}


