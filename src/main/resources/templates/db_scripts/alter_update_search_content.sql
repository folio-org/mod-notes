CREATE OR REPLACE FUNCTION update_search_content()
RETURNS TRIGGER AS $$
BEGIN
  NEW.search_content = ${myuniversity}_mod_notes.f_unaccent(coalesce(NEW.jsonb->>'title','') || ' '
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
