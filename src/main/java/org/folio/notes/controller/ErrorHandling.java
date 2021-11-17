package org.folio.notes.controller;

import static org.folio.notes.util.ErrorsHelper.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.notes.util.ErrorsHelper.ErrorCode.VALIDATION_ERROR;
import static org.folio.notes.util.ErrorsHelper.createInternalError;

import javax.persistence.EntityNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.folio.notes.exception.NoteTypesLimitReached;
import org.folio.tenant.domain.dto.Errors;

@RestControllerAdvice
public class ErrorHandling {

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(EntityNotFoundException.class)
  public Errors handleNotFoundException(EntityNotFoundException e) {
    return createInternalError(e.getMessage(), NOT_FOUND_ERROR);
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  @ExceptionHandler(NoteTypesLimitReached.class)
  public Errors handleConstraintViolationException(NoteTypesLimitReached e) {
    return createInternalError(e.getMessage(), VALIDATION_ERROR);
  }

}
