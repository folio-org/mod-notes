# mod-notes
Notes on all types of objects

The notes module implements a simple CRUD interface on /notes, where users
can post notes, small comment texts that refer to other objects in the system,
for example users or items. See `ramls/note.json` for the precise definition.

The interface provides the usual CRUD operations POST/PUT/GET/DELETE on /notes
and /notes/$id, as well as a GET interface on /notes/_self to list all notes
created by the current user. See the RAML for precise definitions.

The GET interfaces accept a query as usual, for example `notes/?query=link=users`.
Querying on the link is practical to limit to notes on given types of items,
querying on the text is good for searching.
