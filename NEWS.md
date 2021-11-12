## 2.13.1 2021-11-12
* MODNOTES-190 Fix permissions for note-types

## 2.13.0 2021-10-04
* MODNOTES-172 Add script to update function update_search_content()

## 2.12.0 2021-06-09
* MODNOTES-174 Add `class` attribute to whitelist
* MODNOTES-176 Upgrade to RMB 33 and Vert.x 4.1.0.CR1

## 2.11.0 2021-03-01
* MODNOTES-163 Support search by title and content
* Use new api-lint FOLIO-2893
* MODNOTES-167 Upgrade to RMB 32.x and Vert.x 4.0
* MODNOTES-165 Extend note schema to add additional properties
* MODNOTES-171 Add personal data disclosure form

## 2.10.2 2020-11-03
* MODNOTES-158 Fix note content sanitizing
* Update copyright year FOLIO-1021
* Update to RMB v31.1.5 and Vert.x 3.9.4

## 2.10.1 2020-10-23
 * Fix logging issue
 * Update RMB to v31.1.2 and Vertx to 3.9.3

## 2.10.0 2020-10-06
 * MODNOTES-144 - Notes Accordion: Make Note type column heading sortable
 * MODNOTES-141 Extend Note title limit to 255 characters
 * MODNOTES-146 - Notes Accordion: Make Date column heading sortable
 * MODNOTES-147 Make Note title and details column sortable
 * MODNOTES-153 Update to RMB v31.x and JDK 11

## 2.9.0 2020-06-08
 * MODNOTES-150 - Update RMB to v30.0.2
 * MODNOTES-140 - Fix Maven ${} variable replacement in src/main/resources
 * MODNOTES-143 - Securing APIs by default

## 2.8.0 2019-12-02
 * MODNOTES-137 - Set fromModuleVersion attributes
 * MODNOTES-136 - Update RMB version to 29.0.1
 * MODNOTES-132 - Update jackson to 2.10.0 to fix security vulnerabilities
 * MODNOTES-135 - FOLIO-2358 manage container memory
 * FOLIO-2321   - Remove old MD metadata
 * MODNOTES-131 - Upgrade RMB to 27.1.1
 * MODNOTES-130 - Unable to add notes to Agreements/eholdings apps witho…
 * FOLIO-2256   - Enable kube-deploy pipeline
 * FOLIO-2234   - Add LaunchDescriptor settings

## 2.7.0 2019-09-10
 * MODNOTES-121 - Fix error message on POST /notes
 * MODNOTES-128 - Update RMB to 27.0.0 to use "snippetPath" schema attribute
 * MODNOTES-124 - Internal server error during creation of the note
 * MODNOTES-127 - Update folio-service-tools with newer version
 * MODNOTES-117 - Remove dangerous html tags from note content
 * MODNOTES-119 - Fix error message for quering notes
 * MODNOTES-100 - Fix error message when we try to delete a note type 

## 2.6.1 2019-07-24
 * MODNOTES-115 - Ability to filter by notes types
 * MODNOTES-113 - Get funky error message after you exceed Note details character limit
 * MODNOTES-116 - Support the ability to sort Notes by links number
 * MODNOTES-111 - Searching note titles - Support wildcard searching
 * MODNOTES-109 - Set a default Note Type
 * MODNOTES-110 - Note Links: Sorting doesn't work as expected for status = assigned/unassigned
 * MODNOTES-104 - Refactoring: split NoteLinksImpl into fine grained REST / Service / Repository parts
 * MODNOTES-107 - Upgrade to RMB 25
 * MODNOTES-105 - Refactoring: split NotesResourceImpl into fine grained REST / Service / Repository parts

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

