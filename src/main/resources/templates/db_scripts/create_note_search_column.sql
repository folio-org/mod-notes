ALTER TABLE note_data ADD COLUMN IF NOT EXISTS search_content text;

CREATE INDEX search_content_idx_gin ON note_data USING gin (search_content public.gin_trgm_ops);

CREATE OR REPLACE FUNCTION update_search_content()
RETURNS TRIGGER AS $$
BEGIN
  NEW.search_content = f_unaccent(coalesce(NEW.jsonb->>'title','') || ' '
  || regexp_replace(
      regexp_replace(
          coalesce(NEW.jsonb->>'content',''),
          E'<[^>]+>', '', 'gi'
       ),
      '\n+', ' ', 'gi'
    ));
  RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_search_content ON note_data;

CREATE TRIGGER update_search_content
  BEFORE INSERT OR UPDATE ON note_data
  FOR EACH ROW EXECUTE PROCEDURE update_search_content();

UPDATE note_data SET jsonb = jsonb;
