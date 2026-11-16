package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class LunocoIsolatedFeed07Test {
 private val repo = "rgordonSJO/lunoco-updater-isolated-test-07"
 private val pkg = "com.ryan.lunoco.updatertest"
 private val sha = "a".repeat(64)
 private fun api() = """{"id":7,"tag_name":"v1.0.1","draft":false,"prerelease":false,"body":"TEST ONLY",
 "assets":[
 {"name":"Lunoco.apk","state":"uploaded","size":1024,"browser_download_url":"https://github.com/$repo/releases/download/v1.0.1/Lunoco.apk"},
 {"name":"Lunoco-update.json","state":"uploaded","size":300,"browser_download_url":"https://github.com/$repo/releases/download/v1.0.1/Lunoco-update.json"}]}"""
 private fun manifest(packageName: String = pkg) =
  """{"schema":1,"packageName":"$packageName","versionName":"1.0.1","versionCode":3,"sha256":"$sha"}"""
 private fun metadata() = LunocoReleaseProtocol.metadata(LunocoReleaseProtocol.assets(api())!!, manifest(), pkg)

 @Test fun compiledApiIsExactlyTheIsolatedRepository() {
  assertEquals("https://api.github.com/repos/$repo/releases/latest", LunocoReleaseProtocol.API)
 }
 @Test fun compiledAssetUrlsStayOnTheIsolatedRepository() {
  assertEquals("https://github.com/$repo/releases/download/v1.0.1/Lunoco.apk",
   LunocoReleaseProtocol.assetUrl("v1.0.1", "Lunoco.apk"))
 }
 @Test fun productionRepositoryIsNotAnAllowedAssetSourceForTestApp() {
  assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com/rgordonSJO/lunoco/releases/download/v1.0.1/Lunoco.apk"))
 }
 @Test fun similarNamesDoNotBroadenRepositoryTrust() {
  assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com/$repo-evil/releases/download/v1.0.1/Lunoco.apk"))
  assertFalse(LunocoReleaseProtocol.trustedDownload("https://github.com/other/$repo/releases/download/v1.0.1/Lunoco.apk"))
 }
 @Test fun actualParserAcceptsTestBAndRetainsVersionRules() {
  val b=metadata()
  assertTrue(LunocoReleaseProtocol.eligible(b, "1.0.0", 2, true))
  assertFalse(LunocoReleaseProtocol.eligible(b, "1.0.1", 3, true))
  assertFalse(LunocoReleaseProtocol.eligible(b, "1.0.0", 2, false))
 }
 @Test fun productionPackageCannotBeSmuggledThroughTestManifest() {
  assertThrows(Exception::class.java) {
   LunocoReleaseProtocol.metadata(LunocoReleaseProtocol.assets(api())!!,
    manifest("com.ryan.lunatv.jellyfin.performance"), pkg)
  }
 }
 @Test fun productionSignerCannotReplaceTheIsolatedSigner() {
  assertThrows(Exception::class.java) {
   LunocoReleaseProtocol.validateArchive(metadata(), pkg, "1.0.1", 3, 2,
    setOf("45359a801cfc4271823d0822ee64f18ba9b057b71ea7f8c2746aae98a1414d92"),
    setOf("57a0d2a501b9a679311a88aad8d9b41a1b605119ce2cf638694dae2563cf1045"))
  }
 }
 @Test fun compiledBuildConfigIdentifiesOnlyTestNamespaceAndFeed() {
  assertTrue(BuildConfig.APPLICATION_ID.startsWith(pkg))
  assertTrue(BuildConfig.LUNOCO_ISOLATED_TEST)
  assertEquals(repo, BuildConfig.LUNOCO_FEED_REPOSITORY)
  assertNotEquals("com.ryan.lunatv.jellyfin.performance", BuildConfig.APPLICATION_ID)
 }
}
