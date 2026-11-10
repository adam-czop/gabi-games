package com.familygames.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    private val sha = "a".repeat(64)

    private fun json(extra: String = "") = """
        {"schema":1,"versionCode":10300,"versionName":"1.3.0","apkAsset":"familygames-1.3.0-10300.apk",
         "apkSize":18350211,"sha256":"$sha","minSdk":28,"mandatory":false,
         "changelog":["Nowy poziom","Szybciej"],"publishedAt":"2026-11-20T18:00:00Z"$extra}
    """.trimIndent()

    @Test
    fun `parses update json and ignores unknown fields`() {
        val info = UpdateInfo.parse(json(""","futureField":{"x":1}"""))
        assertEquals(10300L, info.versionCode)
        assertEquals(listOf("Nowy poziom", "Szybciej"), info.changelog)
    }

    @Test
    fun `optional fields have defaults`() {
        val info = UpdateInfo.parse("""{"versionCode":2,"versionName":"0.0.2","apkAsset":"a.apk","apkSize":10,"sha256":"$sha"}""")
        assertEquals(28, info.minSdk)
        assertTrue(info.changelog.isEmpty())
    }

    @Test
    fun `broken hash or size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInfo.parse("""{"versionCode":2,"versionName":"0.0.2","apkAsset":"a.apk","apkSize":10,"sha256":"abc"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInfo.parse("""{"versionCode":2,"versionName":"0.0.2","apkAsset":"a.apk","apkSize":0,"sha256":"$sha"}""")
        }
    }

    @Test
    fun `only newer versions on supported devices`() {
        val info = UpdateInfo.parse(json())
        assertEquals(UpdateDecision.Update, UpdateRules.decide(10200, info, deviceSdk = 34))
        assertEquals(UpdateDecision.UpToDate, UpdateRules.decide(10300, info, deviceSdk = 34))
        assertEquals(UpdateDecision.UpToDate, UpdateRules.decide(10400, info, deviceSdk = 34))
        assertEquals(UpdateDecision.DeviceTooOld, UpdateRules.decide(10200, info.copy(minSdk = 35), deviceSdk = 34))
    }

    @Test
    fun `start check every 6 hours`() {
        val h = 60 * 60 * 1000L
        assertTrue(UpdateRules.shouldCheckOnStart(true, lastCheckAt = 0, now = 6 * h))
        assertFalse(UpdateRules.shouldCheckOnStart(true, lastCheckAt = 0, now = 5 * h))
        assertFalse(UpdateRules.shouldCheckOnStart(false, lastCheckAt = 0, now = 100 * h))
    }

    @Test
    fun `install never interrupts a game`() {
        assertFalse(UpdateRules.canInstallNow(onMenu = false, idleMs = 60_000, parentRequested = false, hourOfDay = 14, appInBackground = false))
        assertFalse(UpdateRules.canInstallNow(onMenu = true, idleMs = 5_000, parentRequested = false, hourOfDay = 14, appInBackground = false))
        assertTrue(UpdateRules.canInstallNow(onMenu = true, idleMs = 10_000, parentRequested = false, hourOfDay = 14, appInBackground = false))
        assertTrue(UpdateRules.canInstallNow(onMenu = false, idleMs = 0, parentRequested = true, hourOfDay = 14, appInBackground = false))
        assertTrue(UpdateRules.canInstallNow(onMenu = false, idleMs = 0, parentRequested = false, hourOfDay = 3, appInBackground = true))
        assertFalse(UpdateRules.canInstallNow(onMenu = false, idleMs = 0, parentRequested = false, hourOfDay = 5, appInBackground = true))
    }
}
