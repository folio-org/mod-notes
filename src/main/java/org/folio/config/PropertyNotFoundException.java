package org.folio.config;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class PropertyNotFoundException extends PropertyException {

  public PropertyNotFoundException(String propertyCode) {
    super(propertyCode, "property is not found");
  }

}
