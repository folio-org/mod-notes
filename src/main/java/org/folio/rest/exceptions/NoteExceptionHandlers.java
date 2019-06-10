package org.folio.rest.exceptions;

import java.util.function.Predicate;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.common.pf.PartialFunction;
import org.folio.common.pf.PartialFunctions;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.utils.ValidationHelper;

public class NoteExceptionHandlers {
  private NoteExceptionHandlers() {}

  public static PartialFunction<Throwable, Response> entityValidationHandler() {
    return PartialFunctions.pf((isInstance(InputValidationException.class)), NoteExceptionHandlers::toUnprocessableEntity);
  }

  public static PartialFunction<Throwable, Response> badRequestExtendedHandler() {
    return PartialFunctions.pf(
      isInstance(NotAuthorizedException.class),
      NoteExceptionHandlers::toBadRequest);
  }

  private static Predicate<Throwable> isInstance(Class<?> clazz) {
    return clazz::isInstance;
  }

  private static Response toUnprocessableEntity(Throwable t) {
    InputValidationException validationException = (InputValidationException) t;
    Errors validationErrorMessage = ValidationHelper.createValidationErrorMessage(
      validationException.getField(), validationException.getValue(), validationException.getMessage());
    return Response
      .status(422)
      .header("Content-Type", "application/json")
      .entity(validationErrorMessage).build();
  }

  private static Response toBadRequest(Throwable t) {
    return status(400, t.getMessage());
  }

  private static Response status(int status, String msg) {
    return Response.status(status).type("text/plain").entity(StringUtils.defaultString(msg)).build();
  }
}

