# Change Log

## [Unreleased]

## [v0.3.0] - 2019-02-26
### Changed
- remove paths stored on items
- item upload paths are always dynamically built using `STASH_UPLOAD_DIR`

## [v0.2.0] - 2019-02-25
### Added
- deletion end point
- sample systemd config
- exported default env vars in .env so they can be defined without `export`
  in .env.local and .env.local can be used as a systemd `EnvironmentFile`
- access table for tracking operations
- add list-users command

### Changed
- updated changelog
- add more example usage to readme
- log item count on app startup

## 0.1.0 - 2019-02-20
### Added
- stash service

[Unreleased]: https://github.com/jaemk/stash/compare/v0.3.0...HEAD
[Unreleased]: https://github.com/jaemk/stash/compare/v0.2.0...v0.3.0
[v0.2.0]: https://github.com/jaemk/stash/compare/v0.1.0...v0.2.0
