INSERT INTO type (id, name, created_by, created_date, updated_by, updated_date)
SELECT id,
       jsonb->>'name',
       (jsonb->'metadata'->>'createdByUserId')::uuid,
       to_timestamp(jsonb->'metadata'->>'createdDate', 'YYYY-MM-DDTHH24:MI:SS.MS'),
       (jsonb->'metadata'->>'updatedByUserId')::uuid,
       to_timestamp(jsonb->'metadata'->>'updatedDate', 'YYYY-MM-DDTHH24:MI:SS.MS')
FROM note_type