package com.nuvio.app.features.library

import com.nuvio.app.features.anime.AnimeLibrarySources
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.toggleTrackingLibraryMembership
import com.nuvio.app.features.tracking.trackingMembershipDestinations

/**
 * True when the item can be saved to a connected anime tracker: its tabs are offered only for anime
 * (`supportsItem`) and only while the tracker is connected. The save button then opens the choice.
 */
internal fun hasAnimeLibraryDestinations(tabs: List<TrackingLibraryTab>): Boolean =
    tabs.any { tab -> tab.providerId?.let(AnimeLibrarySources::byProvider) != null }

/**
 * The picker selection: where the item is saved now. An anime saved nowhere gets the destinations
 * chosen the last time, minus the ones no longer offered (e.g. a disconnected service), unless
 * the user removed it from there.
 */
internal fun libraryPickerInitialMembership(
    tabs: List<TrackingLibraryTab>,
    currentMembership: Map<String, Boolean>,
    rememberedDestinations: Set<String>,
    /** True after the user removed the item: it must show as not saved, without preselection. */
    removedByUser: Boolean = false,
): Map<String, Boolean> {
    val membership = tabs.associate { tab -> tab.key to (currentMembership[tab.key] == true) }
    if (removedByUser || !hasAnimeLibraryDestinations(tabs) || membership.values.any { it }) return membership
    // Through the toggle so a selection group (one MAL status) keeps a single choice.
    return trackingMembershipDestinations(tabs)
        .filter { tab -> tab.key in rememberedDestinations }
        .fold(membership) { selection, tab ->
            if (selection[tab.key] == true) selection else toggleTrackingLibraryMembership(tabs, selection, tab.key)
        }
}

/** The anime destinations to remember after a save; null when the item is not an anime. */
internal fun animeLibraryDestinationsToRemember(
    tabs: List<TrackingLibraryTab>,
    membership: Map<String, Boolean>,
): Set<String>? {
    if (!hasAnimeLibraryDestinations(tabs)) return null
    val offered = trackingMembershipDestinations(tabs).mapTo(hashSetOf(), TrackingLibraryTab::key)
    return membership.filter { (key, selected) -> selected && key in offered }.keys
}

/** True when a connected anime tracker lists the item, whatever the library source. */
internal fun isSavedToAnimeTracker(contentId: String, contentType: String?): Boolean =
    TrackingProviderRegistry.connectedLibraryProviders()
        .filter { provider -> AnimeLibrarySources.byProvider(provider.providerId) != null }
        .any { provider -> provider.contains(contentId, contentType) }
