#!/bin/bash
# A script to set up Okapi and run mod-notes
# Requirements
# - Run in mod-notes main directory
# - mod-notes itself compiled ok
# - mod-users in ../mod-users, compiled ok


# Parameters
OKAPIURL="http://localhost:9130"
CURL="curl -w\n -D - "

# Most often used headers
PERM="-HX-Okapi-Permissions:notes.domain.all"
TEN="-HX-Okapi-Tenant:testlib"
JSON="-HContent-type:application/json"
USER="-HX-Okapi-User-Id:99999999-9999-9999-9999-999999999999"

# Check we have the fat jar
if [ ! -f target/mod-notes-fat.jar ]
then
  echo No fat jar found, no point in trying to run
  exit 1
fi

# Check we have mod-users
if [ ! -f ../mod-users/target/mod-users-fat.jar ]
then
  echo No mod-users fat jar found in ../mod-users/target, no point in trying to run
  exit 1
fi

# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi PID=$PID
sleep 1 # give it time to start
echo

# Load mod-users
$CURL -X POST -d@../mod-users/target/ModuleDescriptor.json $OKAPIURL/_/proxy/modules
echo
echo "Deploying it"
$CURL -X POST \
   -d@../mod-users/target/DeploymentDescriptor.json \
   $OKAPIURL/_/discovery/modules
echo


# Load mod-notes
echo "Loading mod-notes"
$CURL -X POST -d@target/ModuleDescriptor.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying it"
$CURL -X POST \
   -d@target/DeploymentDescriptor.json \
   $OKAPIURL/_/discovery/modules
echo


# Test tenant
echo "Creating test tenant"
cat > /tmp/okapi.tenant.json <<END
{
  "id": "testlib",
  "name": "Test Library",
  "description": "Our Own Test Library"
}
END
$CURL -d@/tmp/okapi.tenant.json $OKAPIURL/_/proxy/tenants
echo

echo "Enable mod-users"
$CURL -X POST \
   -d'{"id":"mod-users"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo

echo "Enable mod-notes"
$CURL -X POST \
   -d'{"id":"mod-notes"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo
sleep 1

echo Post our test user
cat > /tmp/user.json <<END
{ "id":"99999999-9999-9999-9999-999999999999",
  "username":"Test user for notes",
  "personal": {
     "lastName": "User",
     "firstName": "Test"
  }
}
END
$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/user.json\
   $OKAPIURL/users
echo

# Various tests
echo Test 1: get empty list
echo $CURL $TEN $PERM $OKAPIURL/notes
$CURL $TEN $PERM $OKAPIURL/notes
echo

echo Test 2: Post one
$CURL $TEN $PERM $USER $JSON \
  -X POST -d '{"id":"44444444-4444-4444-4444-444444444444",
    "link":"users/56789","text":"hello there","domain":"users"}' \
  $OKAPIURL/notes

# Skip the tests

echo Test 3: get a list with the new one
$CURL $TEN $PERM  $OKAPIURL/notes
echo

echo Test 4: Post another one
$CURL $TEN $PERM $JSON $USER\
  -X POST -d '{"link":"items/23456","text":"hello thing", "domain":"items"}' \
  $OKAPIURL/notes

echo Test 5: get a list with both
$CURL $TEN $PERM $OKAPIURL/notes
echo

echo Test 6: query the user note
$CURL $TEN $PERM $OKAPIURL/notes?query=link=users
echo

echo Test 7: query both
$CURL $TEN $PERM $OKAPIURL/notes?query=text=hello
echo

echo Test 8: query both
$CURL $TEN $PERM $OKAPIURL/notes?query='link=*56*'
echo

echo Test 9: Bad queries. Should fail with 422
$CURL $TEN $PERM $OKAPIURL/notes?query='BADQUERY'
echo
$CURL $TEN $PERM $OKAPIURL/notes?query='BADFIELD=foo'
echo
$CURL $TEN $PERM $OKAPIURL/notes?query='metadata.BADFIELD=foo'
echo

echo Test 10: limit
$CURL $TEN $PERM $OKAPIURL/notes?limit=1
echo

echo Test 11: sort
$CURL $TEN $PERM $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.ascending
echo
$CURL $TEN $PERM $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.descending
echo

echo Test 12: permissions
$CURL $TEN \
  -H"X-Okapi-Permissions:notes.domain.users" \
  $OKAPIURL/notes?query='link=*56*'
echo


# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
rm -rf /tmp/postgresql-embed*
ps | grep java && echo "OOPS - Still some processes running"
echo bye

