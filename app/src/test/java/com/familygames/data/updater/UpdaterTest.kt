package com.familygames.data.updater

import com.familygames.domain.parent.FakeSettings
import com.familygames.domain.update.UpdateInfo
import com.familygames.util.Clock
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

private class FakeReader(var archive: ApkFacts?, var installed: ApkFacts) : PackageArchiveReader {
    override fun read(apk: File) = archive
    override fun installed() = installed
}

class ApkVerifierTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(1000) { it.toByte() }
    private fun apk() = tmp.newFile().apply { writeBytes(bytes) }
    private fun info(size: Long = 1000, sha: String = sha256(bytes), version: Long = 2) =
        UpdateInfo(versionCode = version, versionName = "0.0.2", apkAsset = "a.apk", apkSize = size, sha256 = sha)

    private val installed = ApkFacts("com.familygames", 1, setOf("cert"))
    private val good = ApkFacts("com.familygames", 2, setOf("cert"))

    @Test
    fun `valid apk passes`() = assertNull(ApkVerifier(FakeReader(good, installed)).verify(apk(), info()))

    @Test
    fun `every mismatch is reported`() {
        assertEquals(VerifyError.Size, ApkVerifier(FakeReader(good, installed)).verify(apk(), info(size = 999)))
        assertEquals(VerifyError.Hash, ApkVerifier(FakeReader(good, installed)).verify(apk(), info(sha = "0".repeat(64))))
        assertEquals(VerifyError.Unreadable, ApkVerifier(FakeReader(null, installed)).verify(apk(), info()))
        assertEquals(VerifyError.Package, ApkVerifier(FakeReader(good.copy(packageName = "evil"), installed)).verify(apk(), info()))
        assertEquals(VerifyError.Signature, ApkVerifier(FakeReader(good.copy(signerSha256 = setOf("other")), installed)).verify(apk(), info()))
        assertEquals(VerifyError.Version, ApkVerifier(FakeReader(good.copy(versionCode = 3), installed)).verify(apk(), info()))
        assertEquals(VerifyError.NotNewer, ApkVerifier(FakeReader(good.copy(versionCode = 1), installed)).verify(apk(), info(version = 1)))
    }
}

class UpdateManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val apkBytes = ByteArray(50_000) { (it % 251).toByte() }
    private val settings = FakeSettings()
    private val installed = mutableListOf<File>()
    private lateinit var reader: FakeReader

    @Before fun setUp() {
        server.start()
        reader = FakeReader(ApkFacts("com.familygames", 10300, setOf("cert")), ApkFacts("com.familygames", 10200, setOf("cert")))
    }

    @After fun tearDown() = server.close()

    private fun manager(timeoutMs: Long = 5_000) = UpdateManager(
        api = GitHubReleaseApi(
            OkHttpClient.Builder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build(),
            "owner", "repo", "FamilyGames/test", server.url("/"),
        ),
        verifier = ApkVerifier(reader),
        installer = { installed += it },
        settings = settings,
        clock = Clock { 1_000L },
        updatesDir = tmp.newFolder("updates"),
        currentVersionCode = 10200,
        deviceSdk = 34,
    )

    private fun updateJson(version: Long = 10300, sha: String = sha256(apkBytes)) =
        """{"versionCode":$version,"versionName":"1.3.0","apkAsset":"fg.apk","apkSize":${apkBytes.size},"sha256":"$sha","changelog":["Nowość"]}"""

    private fun releaseJson() = """
        {"tag_name":"v1.3.0","assets":[
          {"name":"update.json","size":1,"browser_download_url":"${server.url("/update.json")}"},
          {"name":"fg.apk","size":${apkBytes.size},"browser_download_url":"${server.url("/fg.apk")}"}]}
    """.trimIndent()

    private fun ok(body: String, etag: String? = null) =
        MockResponse.Builder().code(200).body(body).apply { if (etag != null) addHeader("ETag", etag) }.build()

    private fun apkResponse() = MockResponse.Builder().code(200).body(okio.Buffer().write(apkBytes)).build()

    @Test
    fun `newer release is downloaded, verified and installed only when allowed`() = runTest {
        server.enqueue(ok(releaseJson(), etag = "\"e1\""))
        server.enqueue(ok(updateJson()))
        server.enqueue(apkResponse())
        val m = manager()

        val state = m.check()
        assertTrue(state.toString(), state is UpdateState.ReadyToInstall)
        assertEquals(apkBytes.size.toLong(), (state as UpdateState.ReadyToInstall).file.length())
        assertNull(settings.settings.value.updateEtag) // a newer release must be fetched in full next time

        assertFalse(m.installIfReady(onMenu = false, idleMs = 0, parentRequested = false, hourOfDay = 12, appInBackground = false))
        assertTrue(installed.isEmpty())
        assertTrue(m.installIfReady(onMenu = true, idleMs = 15_000, parentRequested = false, hourOfDay = 12, appInBackground = false))
        assertEquals(1, installed.size)
        assertTrue(m.state.value is UpdateState.Installing)
    }

    @Test
    fun `same version is up to date and its etag is reused with 304`() = runTest {
        server.enqueue(ok(releaseJson(), etag = "\"e1\""))
        server.enqueue(ok(updateJson(version = 10200)))
        val m = manager()
        assertTrue(m.check() is UpdateState.UpToDate)
        assertEquals("\"e1\"", settings.settings.value.updateEtag)

        server.enqueue(MockResponse.Builder().code(304).build())
        assertTrue(m.check() is UpdateState.UpToDate)
        server.takeRequest()
        server.takeRequest()
        assertEquals("\"e1\"", server.takeRequest().headers["If-None-Match"])
    }

    @Test
    fun `no release yet is not an error`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertTrue(manager().check() is UpdateState.UpToDate)
    }

    @Test
    fun `tampered apk is rejected and deleted`() = runTest {
        server.enqueue(ok(releaseJson()))
        server.enqueue(ok(updateJson(sha = "f".repeat(64))))
        server.enqueue(apkResponse())
        val m = manager()
        val state = m.check()
        assertEquals(UpdateState.Error(UpdateError.Verification, VerifyError.Hash.name), state)
        assertTrue(tmp.root.resolve("updates").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `interrupted download ends as a network error without leftovers`() = runTest {
        server.enqueue(ok(releaseJson()))
        server.enqueue(ok(updateJson()))
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(apkBytes)).throttleBody(1024, 1, TimeUnit.SECONDS).build())
        val state = manager(timeoutMs = 1_500).check()
        assertTrue(state.toString(), state is UpdateState.Error && state.reason == UpdateError.Network)
        assertTrue(tmp.root.resolve("updates").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `server error is a network error`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        val state = manager().check()
        assertTrue(state is UpdateState.Error && state.reason == UpdateError.Network)
    }
}
