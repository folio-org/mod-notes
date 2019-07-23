## 2.6.0 2019-07-23
 * MODKBEKBJ-290 - Ability to filter by notes types
 * MODNOTES-113 - Get funky error message after you exceed Note details character limit
 * MODKBEKBJ-289 - Support the ability to sort Notes by links number
 * MODNOTES-111 - Searching note titles - Support wildcard searching
 * MODNOTES-109 - Set a default Note Type
 * MODNOTES-110 - Note Links: Sorting doesn't work as expected for status = assigned/unassigned
 * MODNOTES-104 - Refactoring: split NoteLinksImpl into fine grained REST / Service / Repository parts
 * MODNOTES-107 - Upgrade to RMB 25
 * MODNOTES-105 - Refactoring: split NotesResourceImpl into fine grained REST / Service / Repository parts

## 2.5.0 2019-06-06
 * MODNOTES-102 - Add endpoint for searching notes by assignment status
 * MODNOTES-101 - Update to RMB 24 / remove notify dependency
 * MODNOTES-99 - Migrate the code to folio-service-tools library
 * MODNOTES-96 - Check note title and detail limit
 * MODNOTES-93 - Improvements: Modify metadata triggers not to use additional columns
 
## 2.4.0 2019-05-08
 * MODNOTES-97 - Modify Module Descriptor, Dependencies and group id
 * MODNOTES-98 - Fix failing API Tests - missing validate annotation

## 2.3.0 2019-05-07
 * MODNOTES-71 - Notes: Limit the number of note types that can be defined in the system
 * MODNOTES-88 - Note metadata fields are not completely stored on PUT
 * MODNOTES-69 - Notes: Support bulk method to add/remove notes from/to an entity
 * MODNOTES-66 - Notes: Support create new note 
 * MODNOTES-67 - Notes: Support note update 
 * MODNOTES-65 - Notes: Support to GET note by id
 * MODNOTES-61 - Notes: Implement POST new type endpoint
 * MODNOTES-63 - Notes: Implement DELETE type endpoint
 * MODNOTES-59 - Notes: Implement GET type collection endpoint
 * MODNOTES-62 - Implement PUT type endpoint
 * MODNOTES-60 - Implement GET type endpoint
 * MODNOTES-48 - Notes: update note's DB schema to support new fields
 * MODNOTES-58 - Create RAML definition for the endpoints and note type object schema

## 2.2.0 2018-11-30

 * MODNOTES-45 Fix Bad pageable
 * MODNOTES-46 Use notify 2.0

## 2.1.1 2018-09-06
 * MODNOTES-43 Update mod-notes to RAML 1.0

## 2.1.0 2018-06-06
 * MODNOTES-31 "readonly" fields like creatorUserName, etc should be ignorable
 * MODNOTES-34 Update the 'domain-models-runtime' dependency version
 * MODNOTES-37 GET /notes/_self requires unexpected permission
 * MODNOTES-15 Query validation
 * MODNOTES-39 Fix invalid UUIDs
 * general tightening of error responses, using RMB's helpers where possible
 * Code cleanup

## 2.0.1 2017-11-14
 * MODNOTES-24 Upgrade to RMB 15
 * Requires interface 'users' in 14.0 or 15.0
 * MODNOTES-29 /notes endpoint accepts arbitrary key-value pairs
 * MODNOTES-30 Add "populateJsonWithId" to the db-schema json file

## 2.0.0 2017-10-20
 * MODNOTES-19: parse tags and trigger notifications to users mentioned
 * MODNOTES-16: More granular permissions for domains
 * MODNOTES-18: include user full name
 * MODNOTES-23: PUT request wipes out creator details

Breaking changes:
 * Much stricter permissions, need to have notes.domain.XXX, matching the domain
of the notes you operate on (for example, notes.domain.users).
 * PUT request validates that the creatorUserName and creatorLastName are required.
(not so in POST, they get looked up).
 * New dependencies: mod-users and mod-notify

## 1.0.1
 * FOLIO-834

## 1.0.0
 * 'metaData' renamed to 'metadata', due to RMB 14.0.0. This is a BREAKING CHANGE!
   Changes the way records are returned, and what queries are accepted.
 * MODNOTES-13 Execute a git submodule init/update in mvn install
 * More test cases

## 0.2.0
 * MODNOTES-2: Metadata section
 * MODNOTES-3: Unit tests
 * MODNOTES-7: Implement the `_self` endpoint
 * MODNOTES-9: Permissions in the ModuleDescriptor
 * MODNOTES-10: totalRecords instead of total_records

## 0.1.1
* Docker image

## 0.1.0
* Initial implementation: basic CRUD operations in place

