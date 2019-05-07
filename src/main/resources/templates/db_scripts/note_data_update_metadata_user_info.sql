CREATE OR REPLACE FUNCTION set_note_data_md_json()
    RETURNS TRIGGER
AS $$
 DECLARE
    createdDate timestamp WITH TIME ZONE;
    createdBy text ;
    updatedDate timestamp WITH TIME ZONE;
    updatedBy text ;
    injectedMetadata text;
    createdByUsername text;
    updatedByUsername text;
 BEGIN
   createdBy = OLD.jsonb->'metadata'->>'createdByUserId';
   createdDate = OLD.jsonb->'metadata'->>'createdDate';
   createdByUsername = OLD.jsonb->'metadata'->>'createdByUsername';
   updatedBy = NEW.jsonb->'metadata'->>'updatedByUserId';
   updatedDate = NEW.jsonb->'metadata'->>'updatedDate';
   updatedByUsername = NEW.jsonb->'metadata'->>'updatedByUsername';
   if createdBy ISNULL then     createdBy = 'undefined';   end if;
   if updatedBy ISNULL then     updatedBy = 'undefined';   end if;
   if createdByUsername ISNULL then     createdByUsername = 'undefined';   end if;
   if updatedByUsername ISNULL then     updatedByUsername = 'undefined';   end if;
   if createdDate IS NOT NULL
       then injectedMetadata = '{"createdDate":"'||to_char(createdDate,'YYYY-MM-DD"T"HH24:MI:SS.MS')||'" , "createdByUserId":"'||createdBy||'" , "createdByUsername":"'||createdByUsername||'", "updatedDate":"'||to_char(updatedDate,'YYYY-MM-DD"T"HH24:MI:SS.MSOF')||'" , "updatedByUserId":"'||updatedBy||'" , "updatedByUsername":"'|| updatedByUsername||'"}';
       NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata}' ,  injectedMetadata::jsonb , false);
   else
     NEW.jsonb = NEW.jsonb;
   end if;
 RETURN NEW;
 END;
$$
language 'plpgsql';

DROP TRIGGER IF EXISTS set_note_data_md_json_trigger ON note_data CASCADE;

CREATE TRIGGER set_note_data_md_json_trigger BEFORE UPDATE ON note_data   FOR EACH ROW EXECUTE PROCEDURE set_note_data_md_json();

DROP TRIGGER IF EXISTS set_note_data_md_trigger ON note_data CASCADE;

DROP FUNCTION IF EXISTS note_data_set_md();

ALTER TABLE note_data DROP COLUMN IF EXISTS created_by, DROP COLUMN IF EXISTS creation_date;
