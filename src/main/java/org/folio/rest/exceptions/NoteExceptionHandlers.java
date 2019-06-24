package org.folio.rest.exceptions;

import static org.folio.common.pf.PartialFunctions.pf;
import static org.folio.rest.exc.ExceptionPredicates.instanceOf;

import javax.ws.rs.core.Response;

import org.folio.common.pf.PartialFunction;
import org.folio.rest.ResponseHelper;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.utils.ValidationHelper;

public class NoteExceptionHandlers {

  private NoteExceptionHandlers() {}

  public static PartialFunction<Throwable, Response> entityValidationHandler() {
    return pf(instanceOf(InputValidationException.class), NoteExceptionHandlers::toUnprocessableEntity);
  }

  private static Response toUnprocessableEntity(Throwable t) {
    InputValidationException exc = (InputValidationException) t;
    Errors errorMessage = ValidationHelper.createValidationErrorMessage(
      exc.getField(), exc.getValue(), exc.getMessage());
    return ResponseHelper.statusWithJson(422, errorMessage);
  }

}

