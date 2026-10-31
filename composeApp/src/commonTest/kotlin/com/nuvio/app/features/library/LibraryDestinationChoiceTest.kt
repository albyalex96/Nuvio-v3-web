package com.nuvio.app.features.library

import com.nuvio.app.features.anime.decodeLibraryDestinations
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.trackingMembershipGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryDestinationChoiceTest {
    private val local = TrackingLibraryTab("local", "Nuvio Library", null, TrackingLibraryTabKind.WATCHLIST)
    private val traktWatchlist = TrackingLibraryTab("watchlist", "Watchlist", TrackingProviderId.TRAKT, TrackingLibraryTabKind.WATCHLIST)
    private val malWatching = malStatus("watching", TrackingLibraryTabKind.STATUS)
    private val malPlanned = malStatus("plan_to_watch", TrackingLibraryTabKind.WATCHLIST)
    private val animeTabs = listOf(local, traktWatchlist, malWatching, malPlanned)
    private val showTabs = listOf(local, traktWatchlist)

    @Test
    fun onlyItemsWithAnAnimeTrackerDestinationOpenTheChoice() {
        assertTrue(hasAnimeLibraryDestinations(animeTabs))
        assertFalse(hasAnimeLibraryDestinations(showTabs))
    }

    @Test
    fun anAnimeSavedNowhereGetsTheRememberedDestinations() {
        val membership = libraryPickerInitialMembership(
            tabs = animeTabs,
            currentMembership = emptyMap(),
            // Two statuses of the same group: only one stays selected; the unknown list is ignored.
            rememberedDestinations = setOf("local", malWatching.key, malPlanned.key, "trakt:list:gone"),
        )
        assertEquals(true, membership["local"])
        assertEquals(1, listOf(malWatching.key, malPlanned.key).count { membership[it] == true })
        assertEquals(false, membership["watchlist"])
        assertFalse("trakt:list:gone" in membership)
    }

    @Test
    fun aSavedAnimeShowsWhereItIsNotTheMemory() {
        val membership = libraryPickerInitialMembership(
            tabs = animeTabs,
            currentMembership = mapOf("watchlist" to true),
            rememberedDestinations = setOf("local", malPlanned.key),
        )
        assertEquals(mapOf("local" to false, "watchlist" to true, malWatching.key to false, malPlanned.key to false), membership)
    }

    @Test
    fun aRemovedAnimeShowsAsNotSaved() {
        val membership = libraryPickerInitialMembership(
            tabs = animeTabs,
            currentMembership = emptyMap(),
            rememberedDestinations = setOf("local", malPlanned.key),
            removedByUser = true,
        )
        assertTrue(membership.values.none { it })
    }

    @Test
    fun theMemoryIsNotUsedForOtherContent() {
        val membership = libraryPickerInitialMembership(showTabs, emptyMap(), setOf("local"))
        assertEquals(mapOf("local" to false, "watchlist" to false), membership)
    }

    @Test
    fun onlyAnimeChoicesAreRemembered() {
        assertEquals(
            setOf("local", malPlanned.key),
            animeLibraryDestinationsToRemember(
                animeTabs,
                mapOf("local" to true, malPlanned.key to true, "watchlist" to false, "removed" to true),
            ),
        )
        assertNull(animeLibraryDestinationsToRemember(showTabs, mapOf("local" to true)))
    }

    @Test
    fun destinationsAreGroupedByService() {
        val groups = trackingMembershipGroups(animeTabs)
        assertEquals(listOf(null, TrackingProviderId.TRAKT, TrackingProviderId.MAL), groups.map { it.providerId })
        assertEquals(2, groups.last().tabs.size)
    }

    @Test
    fun rememberedDestinationsRoundTrip() {
        assertEquals(setOf("local", "mal:status:watching"), decodeLibraryDestinations("local\n mal:status:watching \n\n"))
        assertEquals(emptySet(), decodeLibraryDestinations(null))
    }

    private fun malStatus(status: String, kind: TrackingLibraryTabKind) = TrackingLibraryTab(
        key = "mal:status:$status",
        title = status,
        providerId = TrackingProviderId.MAL,
        kind = kind,
        selectionGroup = "mal:status",
    )
}
