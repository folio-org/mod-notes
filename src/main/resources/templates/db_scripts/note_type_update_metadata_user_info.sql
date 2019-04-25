CREATE OR REPLACE FUNCTION set_note_type_md_json()
    RETURNS trigger
AS $$
 DECLARE
   createdDate timestamp WITH TIME ZONE;
   createdBy text ;
   updatedDate timestamp WITH TIME ZONE;
   updatedBy text ;
   injectedId text;
   injectedJsonb text;
 BEGIN
   createdBy = NEW.created_by;
   createdDate = NEW.creation_date;
   updatedDate = NEW.jsonb->'metadata'->>'updatedDate';
   updatedBy = NEW.jsonb->'metadata'->>'updatedByUserId';
   injectedJsonb = NEW.jsonb->'metadata';
   if createdBy ISNULL then     createdBy = 'undefined';   end if;
   if updatedBy ISNULL then     updatedBy = 'undefined';   end if;
   if createdDate IS NOT NULL
     then injectedId = '{"createdDate":"'||to_char(createdDate,'YYYY-MM-DD"T"HH24:MI:SS.MS')||'" , "createdByUserId":"'||createdBy||'", "updatedDate":"'||to_char(updatedDate,'YYYY-MM-DD"T"HH24:MI:SS.MSOF')||'" , "updatedByUserId":"'||updatedBy||'"}';
     NEW.jsonb = jsonb_set(NEW.jsonb, '{metadata}' ,  injectedJsonb::jsonb || injectedId::jsonb , false);
   else
     NEW.jsonb = NEW.jsonb;
   end if;
 RETURN NEW;
 END;
$$
language 'plpgsql';

DROP TRIGGER IF EXISTS set_note_type_md_json_trigger ON note_type CASCADE;

CREATE TRIGGER set_note_type_md_json_trigger BEFORE UPDATE ON note_type   FOR EACH ROW EXECUTE PROCEDURE set_note_type_md_json();



