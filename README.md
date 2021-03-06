# stash

> A simple item storage system

## Build/Installation

Build from source or see [releases](https://github.com/jaemk/stash/releases)
for pre-built executables (jre is still required)

```
# generate a standalone jar wrapped in an executable script
$ lein bin
```

## Database

[`migratus`](https://github.com/yogthos/migratus) is used for migration management.

```
# create db
$ sudo -u postgres psql -c "create database stash"

# create user
$ sudo -u postgres psql -c "create user stash"

# set password
$ sudo -u postgres psql -c "alter user stash with password 'stash'"

# allow user to create schemas in database
$ sudo -u postgres psql -c "grant create on database stash to stash"

# allow user to create new databases
$ sudo -u postgres psql -c "alter role stash createdb"

# apply migrations from repl
$ lein with-profile +dev repl
user=> (cmd/migrate!)
```

## Usage

```
# start the server
$ export PORT=3003        # default
$ export REPL_PORT=3999   # default
$ export INSTRUMENT=false # disable spec assertions
$ bin/stash

# connect to running application
$ lein repl :connect 3999
user=> (initenv)  ; loads a bunch of namespaes
user=> (cmd/add-user "you") 
user=> (cmd/list-users)

# upload things
$ curl localhost:4000/create/<my-identifier> \
>   -H 'x-stash-access-token: <access_token>' \
>   --data-binary @some_file.txt
{"ok":"ok","size":<size_in_bytes>,"stash_token":"<stash_token>"}

# download things
$ curl localhost:4000/retrieve/<my-identifier> \
>   -H 'x-stash-access-token: <access_token>' \
>   -d '{"stash_token": "<stash_token>"}' \
>   -o retrieved_file.txt

# delete things
$ curl localhost:4000/delete/<my-identifier> \
>   -H 'x-stash-access-token: <access_token>' \
>   -d '{"stash_token": "<stash_token>"}'
{"ok": "ok"}
```

## Testing

```
# run test
$ lein midje

# or interactively in the repl
$ lein with-profile +dev repl
user=> (autotest)
```

## Docker

```
# build
$ docker build -t stash:latest .
# run
$ docker run --rm -p 3003:3003 -p 3999:3999 --env-file .env.values stash:latest

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
