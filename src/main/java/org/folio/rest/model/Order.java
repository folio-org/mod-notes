package org.folio.rest.model;

public enum Order {

  ASC("asc"), DESC("desc");

  private String value;

  Order(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static boolean contains(String value) {
    for (Order c : Order.values()) {
      if (c.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
