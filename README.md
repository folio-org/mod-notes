# mod-notes

Copyright (C) 2017-2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Notes on all types of objects

The notes module implements a simple CRUD interface on `/notes` where users
can post notes, small comment texts that refer to other objects in the system,
for example users or items. See `ramls/note.json` for the precise definition.

The interface provides the usual CRUD operations POST/PUT/GET/DELETE on `/notes`
and `/notes/$id` as well as a GET interface on `/notes/_self` to list all notes
created by the current user. See the RAML for precise definitions.

The GET interfaces accept a query as usual, for example `notes/?query=domain=users`.
Querying on the domain is practical to limit to notes on given types of items,
querying on the text is good for searching.

For ease of use, the notes contain the username and human readable name (first,
middle, and last) of the creating user. These get automatically populated when
a note is created, if necessary. They can be changed later with a PUT request,
in case the user changes his name.

## User mentions
If the note text contains a tag like @foobar, the module will try to send a
notification to the user whose username is foobar, that he has been mentioned
in a note. If no user is found, the tag is silently ignored. (This feature was
added in version 1.1.4)

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

There is also a overall permission 'notes.domains.all' that grants permission to
all possible domains.

The way this is designed, the notes module does not need to know or care about
which domains we end up having in the system. Unfortunately it requires
mod-permissions to support wildcards in the DesiredPermissions, which it does not
quite do yet.

## Additional information

### Other documentation

Other [modules](http://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](http://dev.folio.org/)

### Issue tracker

See project [MODNOTES](https://issues.folio.org/browse/MODNOTES)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

### Quick start

Compile with `mvn clean install`

Run the local stand-alone instance:

```
java -jar target/mod-notes-fat.jar \
  -Dhttp.port=8085 embed_postgres=true
```

### API documentation

This module's [API documentation](http://dev.folio.org/doc/api/#mod-notes).

The local API docs are available, for example:
```
http://localhost:8085/apidocs/?raml=raml/note.raml
http://localhost:8085/apidocs/?raml=raml/admin.raml
etc.
```

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio.rest%3Amod-notes).

### Download and configuration

The built artifacts for this module are available.
See [configuration](http://dev.folio.org/doc/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-notes/).

