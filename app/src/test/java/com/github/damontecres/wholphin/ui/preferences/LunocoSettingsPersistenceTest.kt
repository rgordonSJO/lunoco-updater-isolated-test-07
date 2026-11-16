package com.github.damontecres.wholphin.ui.preferences

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.data.AppDatabase
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.preferences.UserProfileSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Actual Room round trips, in a disposable in-memory test database; no app/account data is opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LunocoSettingsPersistenceTest {
    @Test fun languageAndProtectionChangesRemainIsolatedByUserAndServer() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.serverDao()
            val serverA = UUID(1, 1)
            val serverB = UUID(1, 2)
            dao.addServer(JellyfinServer(serverA, "A", "http://test-a.invalid", null))
            dao.addServer(JellyfinServer(serverB, "B", "http://test-b.invalid", null))
            val commonUserId = UUID(2, 1)
            val first = dao.addOrUpdateUser(JellyfinUser(id = commonUserId, name = "A", serverId = serverA, accessToken = null))
            val second = dao.addOrUpdateUser(JellyfinUser(id = UUID(2, 2), name = "B", serverId = serverA, accessToken = null))
            val otherServer = dao.addOrUpdateUser(JellyfinUser(id = commonUserId, name = "C", serverId = serverB, accessToken = null))
            dao.updateUser(first.copy(uiLanguage = "fr", requireLogin = true,
                appPreferences = JellyfinUserPreferences(
                    preferredAudioLanguage = "eng",
                    preferredSubtitleLanguage = UserProfileSettings.PREFER_ANY_LANGUAGE,
                    subtitleMode = SubtitleModePreference.ONLY_FORCED,
                )))
            val restored = dao.getUser(serverA, first.id)!!
            assertEquals("fr", restored.uiLanguage)
            assertTrue(restored.requireLogin)
            assertEquals("eng", restored.appPreferences.preferredAudioLanguage)
            assertEquals("_any-language", restored.appPreferences.preferredSubtitleLanguage)
            assertEquals(SubtitleModePreference.ONLY_FORCED, restored.appPreferences.subtitleMode)
            assertEquals(second, dao.getUser(serverA, second.id))
            assertEquals(otherServer, dao.getUser(serverB, commonUserId))
            assertEquals(UserProfileSettings.USE_USER_PROFILE, dao.getUser(serverB, commonUserId)!!.appPreferences.preferredAudioLanguage)
        } finally { db.close() }
    }
}
