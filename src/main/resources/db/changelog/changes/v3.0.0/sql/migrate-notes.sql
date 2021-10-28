INSERT INTO note (id, title, content, indexed_content, domain, type_id, pop_up_on_user, pop_up_on_check_out,
                    created_by, created_date, updated_by, updated_date)
SELECT id,
       jsonb->>'title',
       jsonb->>'content',
       search_content,
       jsonb->>'domain',
       (jsonb->>'typeId')::uuid,
       (jsonb->>'popUpOnUser')::boolean,
       (jsonb->>'popUpOnCheckOut')::boolean,
       (jsonb->'metadata'->>'createdByUserId')::uuid,
       to_timestamp(jsonb->'metadata'->>'createdDate', 'YYYY-MM-DDTHH24:MI:SS.MS'),
       (jsonb->'metadata'->>'updatedByUserId')::uuid,
       to_timestamp(jsonb->'metadata'->>'updatedDate', 'YYYY-MM-DDTHH24:MI:SS.MS')
FROM note_data;