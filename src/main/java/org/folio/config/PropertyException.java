package org.folio.config;

public class PropertyException extends ConfigurationException {

  private final String propertyCode;

  public PropertyException(String propertyCode, String message) {
    super(message);
    this.propertyCode = propertyCode;
  }

  public PropertyException(String propertyCode, Throwable cause) {
    super(cause);
    this.propertyCode = propertyCode;
  }

  public PropertyException(String propertyCode, String message, Throwable cause) {
    super(message, cause);
    this.propertyCode = propertyCode;
  }

  public String getPropertyCode() {
    return propertyCode;
  }

  @Override
  public String getMessage() {
    return "Configuration property '" + propertyCode + "' exception: " + super.getMessage();
  }
}
