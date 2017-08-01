#!/bin/bash

# Parameters
OKAPIURL="http://localhost:9130"
CURL="curl -w\n -D - "

# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi PID=$PID
sleep 1 # give it time to start
echo

# Load mod-notes
echo "Loading mod-notes"
$CURL -X POST -d@ModuleDescriptor.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying it"
$CURL -X POST \
   -d@DeploymentDescriptor.json \
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
echo "Enabliong it"
$CURL -X POST \
   -d'{"id":"mod-notes-0.1.0"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo
sleep 1


# Various tests

$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/notes

# Let it run
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi"
kill $PID

