# Releasing to Maven Central

The build produces everything Maven Central asks for: jars, sources and javadoc jars,
complete POMs, checksums and signatures. The [release workflow](../.github/workflows/release.yml)
uploads them when a version tag is pushed. What it cannot do is own the account and the
signing key; that part is done once, by hand.

**Status: the build side is tested locally (staging layout, signing with a throwaway
key). The upload step has never run, because it needs the account below.**

## One-time setup

1. **Claim the namespace.** Sign in at <https://central.sonatype.com> with the GitHub
   account `qtclover`. The namespace `io.github.qtclover` is then verified automatically.
2. **Create a user token.** On the Central Portal: *Account → Generate User Token*.
   It gives a username and a password.
3. **Create a signing key** and publish its public half:

   ```bash
   gpg --full-generate-key
   ```

   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

   ```bash
   gpg --armor --export-secret-keys <KEY_ID>
   ```

4. **Add four repository secrets** on GitHub (*Settings → Secrets and variables → Actions*):

   | Secret             | Value                                         |
   |--------------------|-----------------------------------------------|
   | `CENTRAL_USERNAME` | the token username from step 2                |
   | `CENTRAL_PASSWORD` | the token password from step 2                |
   | `SIGNING_KEY`      | the whole armoured private key from step 3    |
   | `SIGNING_PASSWORD` | the passphrase of that key                    |

## Each release

1. Set `version` in `gradle.properties` and commit.
2. Tag and push:

   ```bash
   git tag v0.2.0
   ```

   ```bash
   git push origin v0.2.0
   ```

3. The workflow builds, tests, signs and uploads. The upload is validated and then
   waits: open <https://central.sonatype.com/publishing/deployments>, check it, and
   press *Publish*. A published version can never be changed or removed.
4. Update the README, which still says the library is not on Maven Central.

## Trying it without publishing

```bash
./gradlew publishToMavenLocal
```

```bash
./gradlew publishAllPublicationsToStagingRepository
```

The second writes the exact upload layout to `build/staging-deploy`. Signatures are only
produced when `-PsigningKey=...` (or `ORG_GRADLE_PROJECT_signingKey`) is set.
