package org.folio.rest.model;

public enum OrderBy {

  STATUS("status"), TITLE("title");

  private String value;

  OrderBy(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static boolean contains(String value) {
    for (OrderBy orderByValue : OrderBy.values()) {
      if (orderByValue.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
