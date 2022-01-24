CREATE OR REPLACE FUNCTION compute_indexed_content() RETURNS TRIGGER AS $$
  BEGIN
    NEW.indexed_content = coalesce(NEW.title,'') || ' '
    || regexp_replace(regexp_replace(coalesce(NEW.content,''), E'<[^>]+>', '', 'gi'), '\n+', ' ', 'gi');
    RETURN NEW;
  END;
$$ language 'plpgsql';

CREATE TRIGGER compute_indexed_content_trigger
  BEFORE INSERT OR UPDATE ON note
  FOR EACH ROW EXECUTE PROCEDURE compute_indexed_content();