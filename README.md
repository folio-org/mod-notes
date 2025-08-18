# mod-notes

Copyright (C) 2017-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Notes on all types of objects.

The notes module implements a simple CRUD interface where users can post notes,
small comment texts that refer to other objects in the system, for example users or items.

## API

See [API Guide](docs/api-guide.md)

## Permissions
The module declares the usual permission bits for the CRUD operations, but it
also makes use of the DesiredPermissions feature, using one wildcard pattern
'notes.domains.*' for all endpoints. The idea is that each UI module that makes
use of notes, will have to decide on a name for its domain (for example 'items')
and use that on all notes they operate on. They should also define a permission
set that includes the 'notes.domains.items' permission in some permission set,
and enable that for the users who will have access to the permissions on that
domain, together with the CRUD permissions telling what kind of operations the
user is allowed to perform.

There is also an overall permission 'notes.domains.all' that grants permission to
all possible domains.

The way this is designed, the notes module does not need to know or care about
which domains we end up having in the system. Unfortunately it requires
mod-permissions to support wildcards in the DesiredPermissions, which it does not
quite do yet.

## Installing and deployment

### Compiling

Compile with 
```shell
mvn clean install
```

### Running it
Run locally on listening port 8081 (default listening port):

Using Docker to run the local stand-alone instance:

```shell
DB_HOST=localhost DB_PORT=5432 DB_DATABASE=okapi_modules DB_USERNAME=folio_admin DB_PASSWORD=folio_admin \
   java -Dserver.port=8081 -jar target/mod-notes-*.jar
```

### Docker

Build the docker container with:

```shell
docker build -t dev.folio/mod-notes .
```

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### Environment variables

Use `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD` to configure the PostgreSQL database.

`NOTES_TYPES_DEFAULTS_LIMIT` defaults to 25.

`MAX_RECORDS_COUNT` defaults to 1000.

## Additional information

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODNOTES](https://issues.folio.org/browse/MODNOTES)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-notes).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-notes).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-notes/).

