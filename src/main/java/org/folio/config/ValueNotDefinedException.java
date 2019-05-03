package org.folio.config;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class ValueNotDefinedException extends PropertyException {

  public ValueNotDefinedException(String propertyCode) {
    super(propertyCode, "value is not defined");
  }

}
