package org.folio.model;

import static org.apache.commons.lang3.StringUtils.defaultString;

import org.apache.commons.lang3.EnumUtils;

public enum OrderBy {

  STATUS("status"), TITLE("title");

  private String value;

  OrderBy(String value) {
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
    return EnumUtils.isValidEnum(OrderBy.class, defaultString(value).toUpperCase());
  }

  public static OrderBy enumOf(String value) {
    return valueOf(defaultString(value).toUpperCase());
  }
}
