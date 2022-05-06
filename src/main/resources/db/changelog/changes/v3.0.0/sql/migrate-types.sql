INSERT INTO type (id, name, created_by, created_date, updated_by, updated_date)
SELECT id,
       jsonb->>'name',
       CASE WHEN (jsonb->'metadata'->>'createdByUserId')::text = 'undefined' THEN NULL
       ELSE (jsonb->'metadata'->>'createdByUserId')::uuid END,
       to_timestamp(jsonb->'metadata'->>'createdDate', 'YYYY-MM-DDTHH24:MI:SS.MS'),
       CASE WHEN (jsonb->'metadata'->>'updatedByUserId')::text = 'undefined' THEN NULL
       ELSE (jsonb->'metadata'->>'updatedByUserId')::uuid END,
       to_timestamp(jsonb->'metadata'->>'updatedDate', 'YYYY-MM-DDTHH24:MI:SS.MS')
FROM note_type
ON CONFLICT DO NOTHING;
