# stash

> A simple item storage system

## Installation

See releases for standalone jars

or build an uberjar

```
$ lein uberjar
```

## Usage

```
# create application/user access tokens
$ java -jar stash-<version>-standalone.jar add-user --name "my app that needs access to stash"

# start the server
$ java -jar stash-<version>-standalone.jar serve --port 4000
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
