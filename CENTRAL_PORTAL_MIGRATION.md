# Maven Central Portal Migration

This document describes the migration from legacy OSSRH to the new Central Portal for publishing artifacts.

## Changes Made

### 1. Removed Legacy Infrastructure

- **Removed `io.codearte.nexus-staging` plugin** - No longer needed for Central Portal
- **Removed `nexusStaging` configuration** - Manual staging not required
- **Removed manual staging steps from workflow** - Central Portal auto-promotes releases

### 2. Updated Publishing Workflow

The GitHub Actions workflow (`.github/workflows/publish-to-sonatype.yml`) has been updated:

- **Snapshots**: Continue to publish to `https://s01.oss.sonatype.org/content/repositories/snapshots/`
- **Releases**: Publish to `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/` with auto-promotion
- **Removed**: `closeAndReleaseRepository` step - no longer needed

### 3. Added JReleaser Alternative

For full Central Portal API integration, JReleaser plugin has been added:

- **Plugin**: `org.jreleaser` version 1.15.0
- **Configuration**: Pre-configured for Central Portal API
- **Workflow**: Optional step available (commented out)

## Current Setup

### Primary Approach (Active)
Uses the traditional `maven-publish` plugin with existing Sonatype URLs. The Central Portal migration should enable automatic promotion without manual staging.

### Alternative Approach (Available)
JReleaser integration for direct Central Portal API publishing. To activate:

1. Uncomment the JReleaser step in the GitHub workflow
2. Comment out the traditional publishing step
3. Ensure credentials are properly configured

## Migration Benefits

1. **Simplified Process**: No more manual staging and promotion
2. **Faster Releases**: Automatic promotion to Maven Central
3. **Better Integration**: Direct Central Portal API support via JReleaser
4. **Maintained Compatibility**: Existing workflow continues to work

## Credentials

The same credentials are used:
- `SONATYPE_USERNAME` - Your Sonatype account username
- `SONATYPE_PASSWORD` - Your Sonatype account password/token
- `GPG_SIGNING_KEY` - PGP signing key
- `GPG_SIGNING_PASSWORD` - PGP signing password

## Testing

To test the publishing process:

```bash
# Test local publishing
./gradlew publishToMavenLocal

# Test snapshot publishing (requires credentials)
./gradlew publishMavenPublicationToSonatypeRepository -PsimplifyVersion

# Test with JReleaser (requires credentials)
./gradlew jreleaserDeploy
```

## Troubleshooting

If the current approach doesn't work:

1. **Enable JReleaser**: Uncomment the JReleaser step in the workflow
2. **Check Credentials**: Ensure they're updated for Central Portal
3. **URL Updates**: May need to update repository URLs if current ones don't work
4. **Contact Sonatype**: For account-specific migration issues

## References

- [Central Portal vs Legacy OSSRH](https://central.sonatype.org/faq/what-is-different-between-central-portal-and-legacy-ossrh/)
- [JReleaser Maven Central Guide](https://jreleaser.org/guide/latest/examples/maven/maven-central.html#_portal_publisher_api)