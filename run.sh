#!/bin/bash

# Parameters
OKAPIURL="http://localhost:9130"
CURL="curl -w\n -D - "

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
   -d'{"id":"mod-notes-1.0.1-SNAPSHOT"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo
sleep 1


# Various tests
echo Test 1: get empty list
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes
echo

echo Test 2: Post one
$CURL \
  -H "Content-type:application/json" \
  -H "X-Okapi-Tenant:testlib" \
  -H "X-Okapi-User-Id: e037b68e-f202-4a04-9ce2-896a33152b52" \
  -X POST -d '{"link":"users/56789","text":"hello there"}' \
  $OKAPIURL/notes

echo Test 3: get a list with the new one
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes
echo

echo Test 4: Post another one
$CURL \
  -H "Content-type:application/json" \
  -H "X-Okapi-Tenant:testlib" \
  -H "X-Okapi-User-Id: e037b68e-f202-4a04-9ce2-896a33152b52" \
  -X POST -d '{"link":"items/23456","text":"hello thing"}' \
  $OKAPIURL/notes

echo Test 5: get a list with both
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes
echo

echo Test 6: query the user note
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query=link=users
echo

echo Test 7: query both
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query=text=hello
echo

echo Test 8: query both
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query='link=*56*'
echo

echo Test 9: Bad query
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query='link='
echo

echo Test 10: limit
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?limit=1
echo

echo Test 11: sort
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.ascending
echo
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.descending
echo

# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
rm -rf /tmp/postgresql-embed*
echo bye

