package org.folio.rest.exceptions;

public class InputValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String field;
  private final String value;

  public InputValidationException(String field, String value, String message) {
    super(message);
    this.field = field;
    this.value = value;
  }

  public String getField() {
    return field;
  }

  public String getValue() {
    return value;
  }
}
