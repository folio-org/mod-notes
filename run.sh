#!/bin/bash


# Parameters
OKAPIURL="http://localhost:9130"
CURL="curl -w\n -D - "

# Most often used headers
PERM="-HX-Okapi-Permissions:notes.domain.all"
TEN="-HX-Okapi-Tenant:testlib"
JSON="-HContent-type:application/json"
USER="-HX-Okapi-User-Id: 11111111-1111-1111-1111-111111111111"

# Check we have the fat jar
if [ ! -f target/mod-notes-fat.jar ]
then
  echo No fat jar found, no point in trying to run
  exit 1
fi

# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi PID=$PID
sleep 1 # give it time to start
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
echo "Enabling it"
$CURL -X POST \
   -d'{"id":"mod-notes"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo
sleep 1


# Various tests
echo Test 1: get empty list
echo $CURL $TEN $PERM $OKAPIURL/notes
$CURL $TEN $PERM $OKAPIURL/notes
echo

echo Test 2: Post one
$CURL $TEN $PERM $JSON \
  -X POST -d '{"link":"users/56789","text":"hello there","domain":"users"}' \
  $OKAPIURL/notes


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
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
rm -rf /tmp/postgresql-embed*
ps | grep java && echo "OOPS - Still some processes running"
echo bye

