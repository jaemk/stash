# stash

> A simple item storage system

## Installation


```
# generate a standalone jar wrapped in an executable script
$ lein bin
```

## Usage

```
# create application/user access tokens
$ java -jar stash.jar add-user --name "my app that needs access to stash"
Created user <id> with auth token <access_token>

# start the server
$ java -jar stash.jar serve --port 4000

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

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
