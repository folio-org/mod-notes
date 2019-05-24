package org.folio.rest.model;

public enum Status {

  ASSIGNED("ASSIGNED"),
  UNASSIGNED("UNASSIGNED"),
  ALL("ALL");

  private String value;

  Status(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static boolean contains(String value) {
    for (Status c : Status.values()) {
      if (c.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
