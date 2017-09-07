# mod-notes

Copyright (C) 2017 The Open Library Foundation

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

The GET interfaces accept a query as usual, for example `notes/?query=link=users`.
Querying on the link is practical to limit to notes on given types of items,
querying on the text is good for searching.

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

### Download and configuration

The built artifacts for this module are available.
See [configuration](http://dev.folio.org/doc/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-notes/).

