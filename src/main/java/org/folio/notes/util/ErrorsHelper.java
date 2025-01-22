package org.folio.notes.util;

import java.util.List;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.folio.tenant.domain.dto.Error;
import org.folio.tenant.domain.dto.Errors;

@UtilityClass
public class ErrorsHelper {

  public static Error createError(String message, ErrorType type, ErrorCode errorCode) {
    var error = new Error();
    error.setMessage(message);
    error.setType(type.getTypeCode());
    error.setCode(errorCode == null ? null : errorCode.name());
    return error;
  }

  public static Errors createErrors(Error error) {
    var e = new Errors();
    e.setErrors(List.of(error));
    return e;
  }

  public static Errors createUnknownError(String message) {
    return createErrors(createError(message, ErrorType.UNKNOWN, null));
  }

  public static Errors createInternalError(String message, ErrorCode errorCode) {
    return createErrors(createError(message, ErrorType.INTERNAL, errorCode));
  }

  public static Errors createExternalError(String message, ErrorCode errorCode) {
    return createErrors(createError(message, ErrorType.FOLIO_EXTERNAL_OR_UNDEFINED, errorCode));
  }

  @Getter
  public enum ErrorType {
    INTERNAL("-1"),
    FOLIO_EXTERNAL_OR_UNDEFINED("-2"),
    EXTERNAL_OR_UNDEFINED("-3"),
    UNKNOWN("-4");

    private final String typeCode;

    ErrorType(String typeCode) {
      this.typeCode = typeCode;
    }

  }

  public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND_ERROR,
    INTERACTION_ERROR
  }
}
