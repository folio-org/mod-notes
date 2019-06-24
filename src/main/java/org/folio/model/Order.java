package org.folio.model;

import static org.apache.commons.lang3.StringUtils.defaultString;

import org.apache.commons.lang3.EnumUtils;

public enum Order {

  ASC("asc"), DESC("desc");

  private String value;


  Order(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  public static boolean contains(String value) {
    return EnumUtils.isValidEnum(Order.class, defaultString(value).toUpperCase());
  }

  public static Order enumOf(String value) {
    return valueOf(defaultString(value).toUpperCase());
  }
}
