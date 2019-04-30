-- Custom script to create an additional column with the foreign key constraint for note_data table to verify if declared note type id exists.
-- Changes in this file will not result in an update of the function.
-- To change the function, update this script and copy it to the appropriate scripts.snippet field of the schema.json

ALTER TABLE note_data ADD COLUMN IF NOT EXISTS temporary_type_id UUID REFERENCES note_type (id);
CREATE OR REPLACE FUNCTION update_type_id()
RETURNS TRIGGER AS $$
BEGIN
  NEW.temporary_type_id = NEW.jsonb->>'typeId';
  RETURN NEW;
END;
$$ language 'plpgsql';
DROP TRIGGER IF EXISTS update_type_id
  ON note_data;
CREATE TRIGGER update_type_id
  BEFORE INSERT OR UPDATE ON note_data
  FOR EACH ROW EXECUTE PROCEDURE update_type_id();

