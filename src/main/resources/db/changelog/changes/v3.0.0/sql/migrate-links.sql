CREATE TYPE tmp_link_type AS (id text, type text);

INSERT INTO link (id, object_id, object_type)
SELECT gen_random_uuid(),
       (jsonb_populate_record(null::tmp_link_type, links)).*
FROM (SELECT DISTINCT(jsonb_array_elements(jsonb->'links')) AS links FROM note_data) arr;

DROP TYPE tmp_link_type;