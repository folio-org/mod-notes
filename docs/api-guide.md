# Notes API Guide

Details about response and request bodies could be found
in [shared API documentation](https://s3.amazonaws.com/foliodocs/api/mod-notes/s/notes.html).

## Notes API

### Endpoints

| METHOD | URL                                                         | DESCRIPTION                                                                     |
|--------|-------------------------------------------------------------|---------------------------------------------------------------------------------|
| GET    | /notes                                                      | Return notes based on `query` (CQL), `limit`, `offset` params or without params |
| POST   | /notes                                                      | Create new note and related links                                               |
| GET    | /notes/<noteId>                                             | Return note with provided `noteId`                                              |
| PUT    | /notes/<noteId>                                             | Update note and related links with provided `noteId`                            |     
| DELETE | /notes/<noteId>                                             | Delete note and related links with provided `noteId`                            |  
| PUT    | /note-links/type/{objectType}/id/{objectId}                 | Add or delete links to specified list of notes                                  |  
| GET    | /note-links/domain/{domain}/type/{objectType}/id/{objectId} | Return a list of notes by `search`, `noteType`, `status`                        |  

### Supported CQL-query options

| Option             | Example                     | Description                                           |
|--------------------|-----------------------------|-------------------------------------------------------|
| `id`               | `id = "13f21797"`           | Find notes with ID `13f21797`                         |
| `title`            | `title = "title name"`      | Find notes with title `title name`                    |
| `domain`           | `domain = "users"`          | Find notes with domain `users`                        |
| `content`          | `content = "some content"`  | Find notes with content `some content`                |
| `type.id`          | `type.id = "13f21797"`      | Find notes with type ID `13f21797`                    |
| `type.name`        | `type.name = "General"`     | Find notes with type name `General`                   |
| `links.objectId`   | `links.objectId = "12-111"` | Find notes that assigned to record with ID `12-111`   |
| `links.objectType` | `links.objectType = "user"` | Find notes that assigned to record with type `user`   |
| `createdBy`        | `createdBy = "1245478"`     | Find notes that was created by user with ID `1245478` |
| `updatedBy`        | `updatedBy = "1245478"`     | Find notes that was updated by user with ID `1245478` |

## Note Types API

### Endpoints

| METHOD | URL                  | DESCRIPTION                                                                          |
|--------|----------------------|--------------------------------------------------------------------------------------|
| GET    | /note-types          | Return note-types based on `query` (CQL), `limit`, `offset` params or without params |
| POST   | /note-types          | Create new note-type and related links                                               |
| GET    | /note-types/<typeId> | Return note-type with provided `typeId`                                              |
| PUT    | /note-types/<typeId> | Update note-type with provided `typeId`                                              |     
| DELETE | /note-types/<typeId> | Delete note-type with provided `typeId`                                              |

### Supported CQL-query options

| Option      | Example                 | Description                                                |
|-------------|-------------------------|------------------------------------------------------------|
| `id`        | `id = "13f21797"`       | Find note types with ID `13f21797`                         |
| `name`      | `name = "General"`      | Find note types with title `General`                       |
| `createdBy` | `createdBy = "1245478"` | Find note types that was created by user with ID `1245478` |
| `updatedBy` | `updatedBy = "1245478"` | Find note types that was updated by user with ID `1245478` |
