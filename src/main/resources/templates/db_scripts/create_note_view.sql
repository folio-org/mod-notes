-- Custom script to create note_view. Changes in this file will not result in an update of the view.
-- To change the view, update this script and copy it to the appropriate scripts.snippet field of the schema.json
CREATE OR REPLACE VIEW note_view AS
    SELECT nd.id,
           nd.jsonb ||
                jsonb_build_object(
                   'linkTypes',
                   (SELECT array_agg(DISTINCT type) FROM jsonb_to_recordset(nd.jsonb -> 'links') AS x(type text)),
                   'linkIds',
                   (SELECT array_agg(DISTINCT id) FROM jsonb_to_recordset(nd.jsonb -> 'links') AS x(id text)),
                   'type',
                   nt.jsonb -> 'name')
           AS jsonb
    FROM note_data nd
     LEFT JOIN note_type nt ON nd.jsonb->>'typeId' = nt.jsonb->>'id';
