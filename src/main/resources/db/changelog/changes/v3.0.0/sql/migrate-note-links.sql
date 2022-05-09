INSERT INTO note_link (note_id, link_id)
SELECT n.id, l.id
FROM note_data n
CROSS JOIN LATERAL jsonb_array_elements(n.jsonb->'links') AS li(link)
JOIN link l on li.link = jsonb_build_object('id',l.object_id,'type',l.object_type)