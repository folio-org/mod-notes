select note_type.id,
jsonb_build_object('id', note_type.jsonb ->> 'id'::text,
'name', note_type.jsonb ->> 'name'::text,
'usage',json_build_object('noteTotal', count(note_data.jsonb ->> 'id'::text)),
'metadata', note_type.jsonb -> 'metadata'::text)
AS jsonb
FROM diku_mod_notes.note_type
LEFT JOIN diku_mod_notes.note_data ON (note_data.jsonb ->> 'typeId'::text) = (note_type.id)
GROUP BY note_type.id;
