package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.SeriesTrackChoiceDao
import com.github.damontecres.wholphin.data.model.ActivationFlag
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.junit.Assert.*
import org.junit.Test

class StreamChoiceLookupTest {
    private val userId = 42
    private val seriesId = UUID.randomUUID()
    private val item = BaseItemDto(id = UUID.randomUUID(), seriesId = seriesId, type = BaseItemKind.EPISODE)
    private val source = com.github.damontecres.wholphin.test.TestTracks.Builder().buildMediaSourceInfo().copy(
        mediaStreams = listOf(audio(0, "eng", default = true), audio(1, "spa"), subtitle(2, "eng")),
    )
    private val prefs = UserPreferences(AppPreferences.getDefaultInstance(), null)
    private val dao = mockk<SeriesTrackChoiceDao>()
    private val repo = mockServerRepo("eng", SubtitlePlaybackMode.ALWAYS, "eng").also {
        every { it.currentUser } returns JellyfinUser(userId, UUID.randomUUID(), null, UUID.randomUUID(), null)
    }
    private val service = StreamChoiceService(repo, dao)
    private val audioChoice = SeriesTrackChoice(
        userId = userId, parentId = seriesId, type = SeriesTrackChoiceType.AUDIO, language = "spa",
    )
    private val subtitleChoice = audioChoice.copy(
        type = SeriesTrackChoiceType.SUBTITLE, language = "eng", activation = ActivationFlag.DISABLED,
    )

    private fun savedChoices() {
        coEvery { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.AUDIO) } returns listOf(audioChoice)
        coEvery { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.SUBTITLE) } returns listOf(subtitleChoice)
    }

    @Test fun audioLookupUsesAudioHistoryRatherThanDisabledSubtitleHistory() = runTest {
        savedChoices()
        val result = service.chooseAudioStream(source, item, null, null, prefs)
        assertEquals(1, result?.stream?.index)
        coVerify(exactly = 1) { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.AUDIO) }
        coVerify(exactly = 0) { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.SUBTITLE) }
        coVerify(exactly = 0) { dao.save(any<SeriesTrackChoice>()) }
    }

    @Test fun explicitAudioChoicesDoNotRequeryOrChangePrecedence() = runTest {
        val result = service.chooseAudioStream(source, item, null, listOf(audioChoice), prefs)
        assertEquals(1, result?.stream?.index)
        coVerify(exactly = 0) { dao.getBySeriesId(any(), any(), any()) }
    }

    @Test fun savedDisabledSubtitleChoiceStillDisablesSubtitles() = runTest {
        savedChoices()
        val result = service.chooseSubtitleStream(item, source, source.mediaStreams!!.first(), null, null, prefs)
        assertNull(result?.stream)
        coVerify(exactly = 1) { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.SUBTITLE) }
        coVerify(exactly = 0) { dao.getBySeriesId(userId, seriesId, SeriesTrackChoiceType.AUDIO) }
    }
}
