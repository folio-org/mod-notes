package org.folio.util.exc;

import static org.folio.util.pf.PartialFunctions.as;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.util.pf.PartialFunction;
import org.folio.util.pf.PartialFunctions;

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

}
