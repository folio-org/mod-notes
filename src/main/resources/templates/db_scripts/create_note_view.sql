-- Custom script to create note_view. Changes in this file will not result in an update of the view.
-- To change the view, update this script and copy it to the appropriate scripts.snippet field of the schema.json
CREATE OR REPLACE VIEW note_view AS
  SELECT note_data.id,
  jsonb_build_object(
    'id', note_data.jsonb->>'id',
    'title', note_data.jsonb->>'title',
    'domain', note_data.jsonb->>'domain',
    'content', note_data.jsonb->>'content',
    'creator', note_data.jsonb->'creator',
    'updater', note_data.jsonb->'updater',
    'links', note_data.jsonb->'links',
    'linkTypes',
    (SELECT array_agg(DISTINCT type) FROM jsonb_to_recordset(note_data.jsonb->'links') AS x(type text)),
    'linkIds',
    (SELECT array_agg(DISTINCT id) FROM jsonb_to_recordset(note_data.jsonb->'links') AS x(id text)),
    'metadata', note_data.jsonb->'metadata',
    'typeId', note_type.jsonb->'id',
    'type', note_type.jsonb->'name')
  AS jsonb
  FROM note_data
    LEFT JOIN note_type ON note_data.jsonb->>'typeId' = note_type.jsonb->>'id';
