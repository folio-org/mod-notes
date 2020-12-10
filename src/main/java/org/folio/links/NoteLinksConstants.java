package org.folio.links;

class NoteLinksConstants {

  private NoteLinksConstants() {
  }

  static final String NOTE_TABLE = "note_data";
  static final String NOTE_TYPE_TABLE = "note_type";
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

  static final String HAS_LINK_CONDITION = "EXISTS (SELECT FROM jsonb_array_elements(data.jsonb->'links') link WHERE link = ?) ";

  static final String ORDER_BY_STATUS_CLAUSE = "ORDER BY " +
    "(" +
    "CASE WHEN " + HAS_LINK_CONDITION +
    "THEN 'ASSIGNED' " +
    "ELSE 'UNASSIGNED' END" +
    ") %s ";

  static final String ORDER_BY_TITLE_CLAUSE = "ORDER BY data.jsonb->>'title' %s ";

  static final String ORDER_BY_CONTENT_CLAUSE = "ORDER BY search_content %s ";

  static final String ORDER_BY_LINKS_NUMBER = "ORDER BY json_array_length((data.jsonb->>'links')::json) %s ";

  static final String ORDER_BY_NOTE_TYPE = "ORDER BY type.jsonb->>'name' %s ";

  static final String ORDER_BY_UPDATED_DATE = "ORDER BY to_timestamp(data.jsonb->'metadata'->>'updatedDate', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS') %s ";

  static final String LIMIT_OFFSET = "LIMIT ? OFFSET ? ";

  private static final String JOIN_NOTE_TYPE_TABLE = "LEFT JOIN %s as type on (data.jsonb -> 'typeId' = type.jsonb -> 'id') ";

  private static final String WHERE_CLAUSE_BY_DOMAIN_AND_CONTENT =
    "WHERE (data.jsonb->>'domain' = ?) AND search_content ~* f_unaccent(?) ";

  static final String WHERE_CLAUSE_BY_NOTE_TYPE = " AND (type.jsonb ->> 'name' IN (%s)) ";

  static final String SELECT_NOTES_BY_DOMAIN_AND_CONTENT =
    "SELECT data.id, data.jsonb FROM %s as data " + JOIN_NOTE_TYPE_TABLE + WHERE_CLAUSE_BY_DOMAIN_AND_CONTENT;

  static final String COUNT_NOTES_BY_DOMAIN_AND_CONTENT =
    "SELECT COUNT(data.id) as count FROM %s as data " + JOIN_NOTE_TYPE_TABLE + WHERE_CLAUSE_BY_DOMAIN_AND_CONTENT;

  static final String ANY_STRING_PATTERN = ".*";
  static final String WORD_PATTERN = ".*%s.*";
}
