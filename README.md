# TEST ONLY — Lunoco updater acceptance07

This is an isolated updater experiment, NOT official Lunoco.
Do not install for normal viewing or on the primary Guest TV.
Official distribution: https://github.com/rgordonSJO/lunoco

Test package: com.ryan.lunoco.updatertest
Test feed: https://api.github.com/repos/rgordonSJO/lunoco-updater-isolated-test-07/releases/latest

The existing updater/parser/checksum/package/signer/system-installer behavior is retained.
Only repository literals, test application/build identity, isolated signing and TEST label
differ from development source cf590efa6163773f7f5ca076fd49b8b159089e02.
Test1.0.0/code2 and1.0.1/code3 are not production version reservations.

Lunoco derives from Wholphin by damontecres and contributors:
https://github.com/damontecres/Wholphin
GPL-2.0 LICENSE, original source notices, font/component licenses and build inputs are retained.

## Building this test source
Use the Gradle wrapper/toolchain and SDK37. Task :app:assembleDefaultPerformance requires
-Plunoco.isolatedUpdater07=true and paired stable version/code properties.
The marker and test-only configuration refuse accidental shipping builds.
The maintainer TEST key and Windows DPAPI credential are outside this repository,
at ../private/isolated-test07.keystore and ../private/test-password.dpapi.
Other developers must supply their own test-only key/configuration and expected public
fingerprint; their builds cannot replace maintainer-signed APKs. Never use the production key.
No deployment workflow or signing credentials are included.

Publication projection sanitizes TestSeerr fixture addresses and omits the private
artwork verifier. These test-source omissions do not change runtime code.
Mandatory assets: Lunoco.apk and Lunoco-update.json, schema1 fields schema, packageName,
versionName, versionCode, sha256. This test repository does not validate the official feed.
