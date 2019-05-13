#!/bin/bash
# A script to set up Okapi and run mod-notes
# Requirements
# - Run this script in mod-notes main directory
# - okapi in ../okapi, compiled ok
# - mod-notes itself compiled ok
# - mod-permissions in ../mod-permissions, compiled ok
# - mod-users in ../mod-users, compiled ok
# - mod-login in ../mod-login, compiled ok
# - mod-authtoken in ../mod-authtoken, compiled ok
# - mod-notify in ../mod-notify, compiled ok
# - curl installed

# Parameters
OKAPIURL="http://localhost:9130"
OKAPILOG="-Dloglevel=DEBUG"   # comment out if you don't want debug logs
CURL="curl -w\n -D - "

# Most often used headers
PERM="-HX-Okapi-Permissions:notes.domain.users,notes.domain.items,notes.all,users.all"
TEN="-HX-Okapi-Tenant:testlib22"
JSON="-HContent-type:application/json"
USER="-HX-Okapi-User-Id:99999999-9999-4999-9999-999999999999"

# Check we have the fat jar
if [ ! -f target/mod-notes-fat.jar ]
then
  echo No fat jar found, no point in trying to run
  exit 1
fi


# Helper function to load, deploy, and enable a module
# Now that all modules have a good deploymentDescriptor, this is only used
# with one argument, the name of the module.
function mod {
  MODNAME=$1  # name of the module, when enabling it, "mod-users"
  MD=${2:-../$MODNAME/target/ModuleDescriptor.json}
  DD=${3:-../$MODNAME/target/DeploymentDescriptor.json}
  EM=${4:-$MODNAME}
  if [ ! -f ../$MODNAME/target/*-fat.jar ]
  then
    echo "Module ../$MODNAME/target/*-fat.jar not found. No point in going on."
    exit 1
  fi
  echo "###"
  echo "### Loading $MODNAME"
  echo "###"
  if [ ! -f $MD ]
  then
    echo No ModuleDescriptor found for $MODNAME: $MD
    exit 1
  fi
  if [ ! -f $DD ]
  then
    echo "No DeploymentDescriptor for $MODNAME: $DD"
    exit 1
  fi
  $CURL -X POST -d@$MD $OKAPIURL/_/proxy/modules
  echo
  echo "Deploying $MODNAME"
  $CURL -X POST \
     -d@$DD \
     $OKAPIURL/_/discovery/modules
  echo
  echo "Enabling $MODNAME"
  $CURL -X POST \
   -d"{\"id\":\"$EM\"}" \
   $OKAPIURL/_/proxy/tenants/testlib22/modules
echo
}



# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java $OKAPILOG -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi PID=$PID
sleep 2 # give it time to start
echo

# Test tenant
echo "Creating test tenant"
cat > /tmp/okapi.tenant.json <<END
{
  "id": "testlib22",
  "name": "Test Library",
  "description": "Our Own Test Library"
}
END
$CURL -d@/tmp/okapi.tenant.json $OKAPIURL/_/proxy/tenants
echo



######################
# Start the modules


# Start mod-perms first, so we can get out TenantPermissions to work
# (Not strictly necessary any more, enabling mod-permissions will catch up
# with modules enabled earlier.)
mod mod-permissions

# Users
mod mod-users


#################
# mod-login is quite like mod-permissions

mod mod-login



#############
# The notify module is quite standard
mod mod-notify

###################
# mod-notes itself, at last
mod mod-notes



#################
# Set up our test user

echo Post our test user
cat > /tmp/user.json <<END
{ "id":"99999999-9999-4999-9999-999999999999",
  "username":"testuser",
  "active": true,
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


echo Post login user
cat >/tmp/loginuser.json << END
{ "userId":"99999999-9999-4999-9999-999999999999",
  "password":"secretpassword" }
END

$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/loginuser.json\
   $OKAPIURL/authn/credentials


echo List all permsissions
$CURL $TEN \
    $OKAPIURL/perms/permissions?query=permissionName=notes

# We use 'notes.domain.users' and 'notes.domain.items' in our tests.
# These should have been defined in mod-users (missing at the moment),
# and in mod-items (which we don't even have in this test).
# So we just inject them into the permission system, so we can grant
# them to our test user(s)
echo Define our notes.domain permissions
cat > /tmp/domainusers.json << END
{ "permissionName":"notes.domain.users",
  "displayName":"notes for the users domain"}
END
$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/domainusers.json\
   $OKAPIURL/perms/permissions

cat > /tmp/domainitems.json << END
{ "permissionName":"notes.domain.items",
  "displayName":"notes for the items domain"}
END
$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/domainitems.json\
   $OKAPIURL/perms/permissions


echo Post perm user
cat >/tmp/permuser.json << END
{ "userId":"99999999-9999-4999-9999-999999999999",
  "permissions":[ "notes.allops", "notes.domain.users", "notes.domain.items",
    "perms.all",
    "users.all", "users.item.get",
    "notify.all", "notify.collection.get" ] }
END

$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/permuser.json\
   $OKAPIURL/perms/users


###################
# mod-authtoken
# After this, the system is locked down

mod mod-authtoken


###################
# Actual login
# We can reuse the record from when we set the login user
echo
echo "Logging in"
$CURL $TEN $JSON \
   -X POST \
   -d@/tmp/loginuser.json\
   $OKAPIURL/authn/login > /tmp/loginresp.json
cat /tmp/loginresp.json
TOK=-H`grep -i x-okapi-token /tmp/loginresp.json | sed 's/ //' `

echo Received a token $TOK
if [ "$TOK" == "-H" ]
then
  echo "Could not log in, no point in continuing"
  echo "Remember to check/kill running processes"
  exit 1
fi

# Create the domain-specific permissions
# If the permissions are not defined, they don't get expanded, even if we have
# posted them in the perms user.

cat >/tmp/domain.user.perm.json <<END
{
    "permissionName" : "notes.domain.users",
    "displayName" : "Notes - allow access the 'users' domain",
    "description" : "users domain"
  }
END
$CURL $TOK $JSON -X POST \
  -d@/tmp/domain.user.perm.json \
  $OKAPIURL/perms/permissions

cat >/tmp/domain.user.perm.json <<END
{
    "permissionName" : "notes.domain.items",
    "displayName" : "Notes - allow access the 'items' domain",
    "description" : "items domain"
  }
END
$CURL $TOK $JSON -X POST \
  -d@/tmp/domain.user.perm.json \
  $OKAPIURL/perms/permissions

sleep 1


#############
# Various tests

echo Test 0: no permission
$CURL $TEN $OKAPIURL/notes
echo


echo Test 1: get empty list
$CURL $TOK $OKAPIURL/notes
echo

echo Test 2: Post one
$CURL $TOK $JSON \
  -X POST -d '{"id":"44444444-4444-4444-a444-444444444444",
    "link":"users/56789","text":"hello there","domain":"users"}' \
  $OKAPIURL/notes
echo

echo Test 3: get a list with the new one
$CURL $TOK  $OKAPIURL/notes
echo

echo Test 4: Post another one
$CURL $TOK $JSON $USER\
  -X POST -d '{"link":"items/23456", "domain":"items",
    "text":"hello thing @testuser"}' \
  $OKAPIURL/notes
echo

echo Test 5: get a list with both
$CURL $TOK $OKAPIURL/notes
echo

echo Test 6: query the user note
$CURL $TOK $OKAPIURL/notes?query=link=users
echo

echo Test 7: query both
$CURL $TOK $OKAPIURL/notes?query=text=hello
echo

echo Test 8: query both
$CURL $TOK $OKAPIURL/notes?query='link=*56*'
echo

echo Test 9: Bad queries. Should fail with 422
$CURL $TOK $OKAPIURL/notes?query='BADQUERY'
echo
$CURL $TOK $OKAPIURL/notes?query='BADFIELD=foo'
echo
$CURL $TOK $OKAPIURL/notes?query='metadata.BADFIELD=foo'
echo

echo Test 10: limit
$CURL $TOK $OKAPIURL/notes?limit=1
echo

echo Test 11: sort
$CURL $TOK $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.ascending
echo
$CURL $TOK $OKAPIURL/notes?query=text=hello+sortby+link%2Fsort.descending
echo

echo Test 12: permissions - Expect a 403
# Adding a permission to the request doesn't help.
$CURL $TEN \
  -H"X-Okapi-Permissions:notes.domain.users" \
  $OKAPIURL/notes?query='link=*56*'
echo

echo Test 13: permissions for item get - Expect a 403
$CURL $TEN \
  $OKAPIURL/notes/44444444-4444-4444-4444-444444444444
echo

cat >/dev/null << SKIPTHIS


echo Test 14: Post without permission - Expect a 401
# We have domain permissions for 'things' and 'users', not for 'forbidden'
$CURL $TOK $JSON $USER\
  -X POST -d '{"link":"forbidden/23456", "domain":"forbidden",
    "text":"Forbidden @testuser"}' \
  $OKAPIURL/notes
echo

echo Test 15: Show notifications - Should have exactly one
$CURL $TOK \
  $OKAPIURL/notify
echo


# Dummy to disable some part of this script
# Copy the cat line anywhere above this. Leave one copy here!
cat >/dev/null << SKIPTHIS
SKIPTHIS

# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
sleep 1
# Wait for all the modules to stop working
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 2 )
ps | grep java && ( echo ... ; sleep 3 )
ps | grep java && ( echo ... ; sleep 3 )
ps | grep java && ( echo ... ; sleep 4 )
ps | grep java && ( echo ... ; sleep 4 )
ps | grep postgres && ( echo ... ; sleep 2 )
ps | grep postgres && ( echo ... ; sleep 3 )
ps | grep postgres && ( echo ... ; sleep 4 )
ps | grep postgres && ( echo ... ; sleep 5 )
ps | grep postgres && ( echo ... ; sleep 6 )
ps | grep postgres && ( echo ... ; sleep 7 )
ps | grep postgres && ( echo ... ; sleep 8 )
rm -rf /tmp/postgresql-embed*
ps | grep java | grep -v "grep java" && echo "OOPS - Still some java processes running"
ps | grep post | grep -v "grep post" && echo "OOPS - Still some postgres processes running"
echo bye

