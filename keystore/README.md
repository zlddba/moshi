# Signing keystore

Put the release keystore in this folder, for example:

```
keystore/moshi-release.jks
```

This folder is **git-ignored** — only this README is tracked. The keystore file and its
passwords must never be committed: anyone holding them could sign APKs that appear to
come from this project.

## Local release builds

Create `keystore.properties` in the repository root (also git-ignored):

```properties
storeFile=keystore/moshi-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`app/build.gradle.kts` reads that file. When it is absent the release build simply
produces an unsigned APK, so debug builds and CI checkouts keep working without it.

## CI (GitHub Actions)

The release workflow reconstructs both the keystore and the properties file from
repository secrets, so no key material is ever stored in git. See
`.github/workflows/release.yml` for the exact secret names.
