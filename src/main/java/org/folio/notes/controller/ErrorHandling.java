package org.folio.notes.controller;

import static org.folio.notes.util.ErrorsHelper.ErrorCode.NOTE_TYPES_LIMIT_REACHED;
import static org.folio.notes.util.ErrorsHelper.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.notes.util.ErrorsHelper.ErrorCode.VALIDATION_ERROR;
import static org.folio.notes.util.ErrorsHelper.createError;
import static org.folio.notes.util.ErrorsHelper.createErrors;
import static org.folio.notes.util.ErrorsHelper.createInternalError;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.notes.exception.ResourceNotFoundException;
import org.folio.notes.util.ErrorsHelper;
import org.folio.spring.cql.CqlQueryValidationException;
import org.folio.tenant.domain.dto.Errors;
import org.folio.tenant.domain.dto.Parameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErrorHandling {

  private static final String LIMIT_PARAMETER = "limit";

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ResourceNotFoundException.class)
  public Errors handleNotFoundException(ResourceNotFoundException e) {
    return createInternalError(e.getMessage(), NOT_FOUND_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(NoteTypesLimitReached.class)
  public Errors handleConstraintViolationException(NoteTypesLimitReached e) {
    var error = createError(e.getMessage(), ErrorsHelper.ErrorType.INTERNAL, NOTE_TYPES_LIMIT_REACHED);
    error.setParameters(List.of(new Parameter(LIMIT_PARAMETER).value(String.valueOf(e.getLimit()))));
    return createErrors(error);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(ConstraintViolationException.class)
  public Errors handleConstraintViolationException(ConstraintViolationException e) {
    return createInternalError(e.getMessage(), VALIDATION_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public Errors handleDataIntegrityViolationException(DataIntegrityViolationException e) {
    var localizedMessage = e.getMostSpecificCause().getLocalizedMessage();
    var message = StringUtils.substringAfter(localizedMessage, "Detail:").trim();
    return createInternalError(message, VALIDATION_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class,
    CqlQueryValidationException.class,
    IllegalArgumentException.class
  })
  public Errors handleMissingParameterException(Exception e) {
    return createInternalError(e.getLocalizedMessage(), VALIDATION_ERROR);
  }
}
