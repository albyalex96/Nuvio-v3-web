package com.nuvio.tv.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildSearchDeepLinkUrl
import com.nuvio.tv.R

/**
 * Android TV global search ("Searchable apps"): the system asks this provider for suggestions
 * while the user searches from the remote, even with the app closed. It returns a single row,
 * "Search «query» in Nuvio", whose `nuvio://search` link opens the app on its search screen
 * (which then jumps to the details when a single title matches).
 *
 * It does not touch any app repository: the process may be started just for this query,
 * before the activity has initialized storages.
 */
class TvSearchSuggestionsProvider : ContentProvider() {

    private val log = Logger.withTag("TvGlobalSearch")

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val query = extractQuery(uri, selectionArgs)
        if (query.isEmpty()) {
            log.d { "Global search query without text" }
            return cursor
        }
        val context = context ?: return cursor
        log.i { "Global search suggestion requested (${query.length} chars)" }
        cursor.addRow(
            arrayOf<Any?>(
                1L,
                context.getString(R.string.tv_global_search_suggestion, query),
                context.getString(R.string.tv_global_search_suggestion_subtitle),
                "android.resource://${context.packageName}/${context.applicationInfo.icon}",
                android.content.Intent.ACTION_VIEW,
                buildSearchDeepLinkUrl(query),
                query,
            ),
        )
        return cursor
    }

    private fun extractQuery(uri: Uri, selectionArgs: Array<out String>?): String {
        val fromArgs = selectionArgs?.firstOrNull()?.trim().orEmpty()
        if (fromArgs.isNotEmpty()) return fromArgs
        val last = uri.lastPathSegment?.trim().orEmpty()
        return if (last == SearchManager.SUGGEST_URI_PATH_QUERY) "" else last
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_QUERY,
        )
    }
}
