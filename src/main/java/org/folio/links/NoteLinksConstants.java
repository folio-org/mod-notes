package org.folio.links;

class NoteLinksConstants {

  private NoteLinksConstants() {
  }

  static final String NOTE_TABLE = "note_data";
  static final String INSERT_LINKS =
    "UPDATE %s " +
      "SET jsonb = jsonb_insert(jsonb, '{links, -1}', ?, true) " +
      "WHERE NOT EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ? ) AND " +
      "id IN (%s)";

  /**
   * in this query, jsonb_set function replaces old jsonb->links array with new one,
   * "-" is an operator that removes an element by index
   * and (select MIN(position)-1 ...) is a subquery that calculates index of first element that matches searched link
   */
  static final String REMOVE_LINKS =
    "UPDATE %s " +
      "SET jsonb = jsonb_set(jsonb, '{links}',  " +
      "(jsonb->'links') " +
      " - " +
      "(SELECT MIN(position)-1 FROM jsonb_array_elements(jsonb->'links') WITH ORDINALITY links(link, position) WHERE link = ?)::int) " +
      "WHERE id IN (%s) " +
      "AND EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?)";

  static final String DELETE_NOTES_WITHOUT_LINKS =
    "DELETE FROM %s " +
      "WHERE id IN (%s) AND " +
      "jsonb->'links' = '[]'::jsonb";

  static final String HAS_LINK_CONDITION = "EXISTS (SELECT FROM jsonb_array_elements(jsonb->'links') link WHERE link = ?) ";

  static final String ORDER_BY_STATUS_CLAUSE = "ORDER BY " +
    "(" +
    "CASE WHEN " + HAS_LINK_CONDITION +
    "THEN 'ASSIGNED'" +
    "ELSE 'UNASSIGNED' END" +
    ") %s ";

  static final String ORDER_BY_TITLE_CLAUSE = "ORDER BY jsonb->>'title' %s ";

  static final String LIMIT_OFFSET = "LIMIT ? OFFSET ? ";

  static final String SELECT_NOTES_BY_DOMAIN_AND_TITLE =
    "SELECT id, jsonb FROM %s WHERE (jsonb->>'domain' = ?) AND " +
      "(f_unaccent(jsonb->>'title') ~* f_unaccent(?)) ";

  static final String COUNT_NOTES_BY_DOMAIN_AND_TITLE =
    "SELECT COUNT(id) as count FROM %s WHERE (jsonb->>'domain' = ?) AND " +
      "(f_unaccent(jsonb->>'title') ~* f_unaccent(?)) ";

  static final String ANY_STRING_PATTERN = ".*";
  static final String WORD_PATTERN = "\\m%s\\M";
}
