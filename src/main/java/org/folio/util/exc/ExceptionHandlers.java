package org.folio.util.exc;

import static org.folio.util.pf.PartialFunctions.as;

import java.util.function.Function;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.HttpStatus;

import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.util.pf.PartialFunction;

public class ExceptionHandlers {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlers.class);

  private ExceptionHandlers() {
  }

  public static PartialFunction<Throwable, Response> badRequestHandler() {
    return as(BadRequestException.class::isInstance, ExceptionHandlers::toBadRequest);
  }

  public static PartialFunction<Throwable, Response> notFoundHandler() {
    return as(NotFoundException.class::isInstance, ExceptionHandlers::toNotFound);
  }

  public static PartialFunction<Throwable, Response> generalHandler() {
    return as(t -> true, ExceptionHandlers::toGeneral);
  }

  public static Function<Throwable, Response> logged(PartialFunction<Throwable, Response> pf) {
    return throwable -> {
      LOGGER.error("Execution failed with: " + throwable.getMessage(), throwable);
      return pf.apply(throwable);
    };
  }

  private static Response toBadRequest(Throwable t) {
    return Response.status(HttpStatus.SC_BAD_REQUEST)
      .type(MediaType.TEXT_PLAIN)
      .entity(t.getMessage())
      .build();
  }

  private static Response toNotFound(Throwable t) {
    return Response.status(HttpStatus.SC_NOT_FOUND)
      .type(MediaType.TEXT_PLAIN)
      .entity(t.getMessage())
      .build();
  }

  private static Response toGeneral(Throwable t) {
    Future<Response> validationFuture = Future.future();
    ValidationHelper.handleError(t, validationFuture);

    if (validationFuture.succeeded()) {
      return validationFuture.result();
    } else {
      return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
        .type(MediaType.TEXT_PLAIN)
        .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
        .build();
    }
  }
}
