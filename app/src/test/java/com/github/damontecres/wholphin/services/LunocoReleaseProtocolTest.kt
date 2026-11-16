package com.github.damontecres.wholphin.services

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LunocoReleaseProtocolTest {
    private val pkg = "com.ryan.lunatv.jellyfin.performance"
    private val hash = "a".repeat(64)
    private fun release(tag: String = "v1.1.0", draft: Boolean = false, pre: Boolean = false,
        apk: Boolean = true, manifest: Boolean = true, url: String? = null, digest: String? = "sha256:$hash") =
        buildJsonObject {
            put("id", 10); put("tag_name", tag); put("draft", draft); put("prerelease", pre)
            put("body", "Notes: https://untrusted.invalid/install.apk")
            putJsonArray("assets") {
                for (name in listOfNotNull("Lunoco.apk".takeIf { apk }, "Lunoco-update.json".takeIf { manifest })) {
                    addJsonObject {
                        put("name", name); put("state", "uploaded"); put("size", 100)
                        put("browser_download_url", url ?: LunocoReleaseProtocol.assetUrl(tag, name))
                        if (name.endsWith(".apk") && digest != null) put("digest", digest)
                    }
                }
            }
        }.toString()
    private fun manifest(name: String = "1.1.0", code: Long = 11, packageName: String = pkg, sha: String = hash) =
        """{"schema":1,"packageName":"$packageName","versionName":"$name","versionCode":$code,"sha256":"$sha"}"""
    private fun metadata() = LunocoReleaseProtocol.metadata(LunocoReleaseProtocol.assets(release())!!, manifest(), pkg)
    private fun rejected(block: () -> Unit) { assertThrows(Exception::class.java, block) }

    @Test fun newerStableReleaseIsEligible() {
        assertTrue(LunocoReleaseProtocol.eligible(metadata(), "1.0.0", 10, true))
    }
    @Test fun sameVersionIsNeverAdvertised() {
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "1.1.0", 11, true))
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "1.1.0", 10, true))
    }
    @Test fun olderVersionAndDowngradeCodesAreRejected() {
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "2.0.0", 1, true))
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "1.0.0", 12, true))
    }
    @Test fun developmentSuffixesAreNotComparedLexicographically() {
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "0.0.0-local-perf-newer", 1, false))
        assertFalse(LunocoReleaseProtocol.eligible(metadata(), "1.0.0-dev", 1, true))
        assertNull(LunocoReleaseProtocol.version("1.2.3-beta"))
    }
    @Test fun draftsAreRejected() { rejected { LunocoReleaseProtocol.assets(release(draft = true)) } }
    @Test fun prereleasesAreRejected() { rejected { LunocoReleaseProtocol.assets(release(pre = true)) } }
    @Test fun malformedAndMissingMetadataAreRejected() {
        rejected { LunocoReleaseProtocol.assets("{") }
        rejected { LunocoReleaseProtocol.assets("{}") }
        rejected { LunocoReleaseProtocol.metadata(LunocoReleaseProtocol.assets(release())!!, "{}", pkg) }
    }
    @Test fun missingApkIsRejected() { rejected { LunocoReleaseProtocol.assets(release(apk = false)) } }
    @Test fun missingManifestIsRejected() { rejected { LunocoReleaseProtocol.assets(release(manifest = false)) } }
    @Test fun notesCannotSupplyExecutableDownloadUrls() {
        assertEquals(LunocoReleaseProtocol.assetUrl("v1.1.0", "Lunoco.apk"), metadata().apkUrl)
        rejected { LunocoReleaseProtocol.assets(release(url = "https://untrusted.invalid/Lunoco.apk")) }
    }
    @Test fun trustedRedirectsStayHttpsAndOnApprovedHosts() {
        assertTrue(LunocoReleaseProtocol.trustedDownload("https://release-assets.githubusercontent.com/github-production-release-asset/a?sig=x"))
        assertFalse(LunocoReleaseProtocol.trustedDownload("http://github.com/rgordonSJO/lunoco/releases/download/v1/Lunoco.apk"))
        assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com/other/lunoco/releases/download/v1/Lunoco.apk"))
        assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com@evil.invalid/Lunoco.apk"))
        assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com:444/rgordonSJO/lunoco/releases/download/v1/Lunoco.apk"))
    }
    @Test fun manifestMustMatchTagAndPackage() {
        val a = LunocoReleaseProtocol.assets(release())!!
        rejected { LunocoReleaseProtocol.metadata(a, manifest(packageName = "another.app"), pkg) }
        rejected { LunocoReleaseProtocol.metadata(a, manifest(name = "2.0.0"), pkg) }
    }
    @Test fun versionCodesMustBeReleaseCompatible() {
        val a = LunocoReleaseProtocol.assets(release())!!
        listOf(0L, 1L, 2_100_000_001L).forEach { rejected { LunocoReleaseProtocol.metadata(a, manifest(code = it), pkg) } }
    }
    @Test fun expectedDigestMustBeValidAndAgreesWithGithubWhenAvailable() {
        val a = LunocoReleaseProtocol.assets(release())!!
        rejected { LunocoReleaseProtocol.metadata(a, manifest(sha = "not a checksum"), pkg) }
        rejected { LunocoReleaseProtocol.metadata(a, manifest(sha = "b".repeat(64)), pkg) }
    }
    @Test fun independentlyRetrievedManifestRemainsRequiredWithoutApiDigest() {
        val a = LunocoReleaseProtocol.assets(release(digest = null))!!
        assertEquals(hash, LunocoReleaseProtocol.metadata(a, manifest(), pkg).sha256)
        rejected { LunocoReleaseProtocol.metadata(a, "{}", pkg) }
    }
    private fun archive(packageName: String = pkg, version: String = "1.1.0", code: Long = 11,
        installedCode: Long = 10, installed: Set<String> = setOf("original"), signed: Set<String> = setOf("original")) =
        LunocoReleaseProtocol.validateArchive(metadata(), packageName, version, code, installedCode, installed, signed)
    @Test fun exactPackageVersionAndSignerAreAccepted() { archive() }
    @Test fun wrongPackageIsRejected() { rejected { archive(packageName = "other.app") } }
    @Test fun wrongSigningCertificateIsRejected() { rejected { archive(signed = setOf("different")) } }
    @Test fun emptyOrUnverifiedRotatedSignersAreRejected() {
        rejected { archive(installed = emptySet(), signed = emptySet()) }
        rejected { archive(signed = setOf("original", "rotated")) }
    }
    @Test fun wrongArchiveVersionAndNonIncreasingCodeAreRejected() {
        rejected { archive(version = "1.1.1") }
        rejected { archive(code = 12) }
        rejected { archive(installedCode = 11) }
    }
    @Test fun automaticThrottleAndManualBypassHaveSeparateBoundaries() {
        val policy = UpdateSessionPolicy()
        assertFalse(policy.mayCheck(100, 200, false))
        assertTrue(policy.mayCheck(100, 200, true))
        assertTrue(policy.mayCheck(200, 200, false))
    }
    @Test fun offersRequireReadyIdleForegroundHomeWithoutAnotherWindow() {
        val policy = UpdateSessionPolicy()
        assertTrue(policy.mayPrompt(false, true, 30_000, true, true))
        assertFalse(policy.mayPrompt(false, false, 30_000, true, true))
        assertFalse(policy.mayPrompt(false, true, 29_999, true, true))
        assertFalse(policy.mayPrompt(false, true, 30_000, false, true))
        assertFalse(policy.mayPrompt(false, true, 30_000, true, false))
    }
    @Test fun laterSuppressesAutomaticOffersForSessionButNotExplicitManualChecks() {
        val policy = UpdateSessionPolicy().apply { prompted = true }
        assertFalse(policy.mayPrompt(false, true, 90_000, true, true))
        assertTrue(policy.mayPrompt(true, false, 0, true, true))
        assertFalse(policy.mayPrompt(true, true, 0, false, true))
    }
}
