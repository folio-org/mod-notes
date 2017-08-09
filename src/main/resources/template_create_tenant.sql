-- Script to be run at the _tenant call, when module is enabled for a tenant
-- The values 'myuniversity' and 'mymodule' will be replaced with the tenant
-- and module names.

-- remove access to public schema to all
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- create tenant user in db
-- DROP USER IF EXISTS myuniversity;

CREATE USER myuniversity_mymodule WITH ENCRYPTED PASSWORD 'myuniversity';
GRANT myuniversity_mymodule TO CURRENT_USER;
ALTER USER myuniversity_mymodule WITH CONNECTION LIMIT 50;

-- remove this
-- GRANT ALL PRIVILEGES ON DATABASE postgres TO myuniversity;

-- create table space per tenant
-- CREATE TABLESPACE ts_myuniversity OWNER myuniversity LOCATION '${tablespace_dir}/myuniversity/module_to/module_from';
-- SET default_tablespace = ts_myuniversity;

-- DROP SCHEMA IF EXISTS myuniversity CASCADE;
-- The schema user wil be the schema name since not given
CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

-- for uuid generator -> gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set the new schema first so that we dont have to namespace when creating tables
-- add the postgres to the search path so that we can use the pgcrypto extension
SET search_path TO myuniversity_mymodule, public;

CREATE TABLE IF NOT EXISTS note_data (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   jsonb jsonb,
   creation_date timestamp WITH TIME ZONE,
   created_by  text
);

-- index to support @> ops, faster than jsonb_ops
CREATE INDEX idxgin_conf ON note_data USING gin (jsonb jsonb_path_ops);


-- give the user PRIVILEGES after everything is created by script
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA myuniversity_mymodule TO myuniversity_mymodule;

-- We do not need initial data. Kept here as an example. Can copy what ever kind of data
-- COPY note_data (jsonb) FROM 'data/locales.data' ENCODING 'UTF8';

-- Automagic metadata
-- At some point this will be moved into rmb. Now just pasted here...

-- auto populate the meta data schema

-- on create of user record - pull creation date and creator into dedicated column - rmb makes auto-populates these fields in the md fields
CREATE OR REPLACE FUNCTION set_md()
RETURNS TRIGGER AS $$
BEGIN
  NEW.creation_date = to_timestamp(NEW.jsonb->'metaData'->>'createdDate', 'YYYY-MM-DD"T"HH24:MI:SS.MS');
  NEW.created_by = NEW.jsonb->'metaData'->>'createdByUserId';
  RETURN NEW;
END;
$$ language 'plpgsql';
CREATE TRIGGER set_md_trigger BEFORE INSERT ON myuniversity_mymodule.note_data
   FOR EACH ROW EXECUTE PROCEDURE  set_md();

-- on update populate md fields from the creation date and creator fields
CREATE OR REPLACE FUNCTION set_md_json()
RETURNS TRIGGER AS $$
DECLARE
  createdDate timestamp WITH TIME ZONE;
  createdBy text ;
  updatedDate timestamp WITH TIME ZONE;
  updatedBy text ;
  injectedId text;
BEGIN
  createdBy = NEW.created_by;
  createdDate = NEW.creation_date;
  updatedDate = NEW.jsonb->'metaData'->>'updatedDate';
  updatedBy = NEW.jsonb->'metaData'->>'updatedByUserId';

  if createdBy ISNULL then
    createdBy = 'undefined';
  end if;
  if updatedBy ISNULL then
    updatedBy = 'undefined';
  end if;
  if createdDate IS NOT NULL then
-- creation date and update date will always be injected by rmb - if created date is null it means that there is no meta data object
-- associated with this object - so only add the meta data if created date is not null -- created date being null may be a problem
-- and should be handled at the app layer for now -- currently this protects against an exception in the db if no md is present in the json
    injectedId = '{"createdDate":"'||to_char(createdDate,'YYYY-MM-DD"T"HH24:MI:SS.MS')||'" , "createdByUserId":"'||createdBy||'", "updatedDate":"'||to_char(updatedDate,'YYYY-MM-DD"T"HH24:MI:SS.MSOF')||'" , "updatedByUserId":"'||updatedBy||'"}';
    NEW.jsonb = jsonb_set(NEW.jsonb, '{metaData}' ,  injectedId::jsonb , false);
  end if;
RETURN NEW;

END;
$$ language 'plpgsql';
CREATE TRIGGER set_md_json_trigger BEFORE UPDATE ON myuniversity_mymodule.note_data
  FOR EACH ROW EXECUTE PROCEDURE set_md_json();

-- --- end auto populate meta data schema ------------
