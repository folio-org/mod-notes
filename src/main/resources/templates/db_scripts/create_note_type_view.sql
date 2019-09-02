CREATE OR REPLACE VIEW note_type_view AS
  SELECT note_type.id, jsonb_build_object(
  'id', note_type.jsonb ->> 'id'::text,
  'name', note_type.jsonb ->> 'name'::text,
  'usage', json_build_object('noteTotal', count(note_data.jsonb ->> 'id'::text)),
  'metadata', note_type.jsonb -> 'metadata'::text)
  AS jsonb
  FROM note_type
    LEFT JOIN note_data ON (note_data.jsonb ->> 'typeId')::uuid = (note_type.id)
    GROUP BY note_type.id;
