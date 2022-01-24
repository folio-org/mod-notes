INSERT INTO note_link (note_id, link_id)
SELECT n.id, l.id
FROM note_data n
JOIN link l ON EXISTS (SELECT FROM jsonb_array_elements(n.jsonb->'links') link WHERE link =
					  jsonb_build_object('id',l.object_id,'type',l.object_type))