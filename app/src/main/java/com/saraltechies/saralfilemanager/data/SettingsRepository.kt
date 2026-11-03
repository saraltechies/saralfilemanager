package com.saraltechies.saralfilemanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileSort
import com.saraltechies.saralfilemanager.files.Gallery
import com.saraltechies.saralfilemanager.files.GallerySort
import com.saraltechies.saralfilemanager.files.SortBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** How folders are shown. Applies everywhere. */
data class ViewPrefs(
    val sortBy: SortBy = SortBy.NAME,
    val descending: Boolean = false,
    val showHidden: Boolean = false,
    val grid: Boolean = false,
) {
    val comparator: Comparator<FileItem> get() = FileSort.comparator(sortBy, descending)
}

data class GalleryPrefs(val columns: Int, val sort: GallerySort, val list: Boolean)

class SettingsRepository(private val context: Context) {

    private val sortByKey = stringPreferencesKey("sort_by")
    private val descendingKey = booleanPreferencesKey("sort_descending")
    private val showHiddenKey = booleanPreferencesKey("show_hidden")
    private val gridKey = booleanPreferencesKey("grid")
    private val useBinKey = booleanPreferencesKey("use_bin")
    private val notificationAskedKey = booleanPreferencesKey("notification_asked")
    private val bookmarksKey = stringSetPreferencesKey("bookmarks")
    private val goodMomentsKey = intPreferencesKey("good_moments")
    private val reviewPromptsKey = intPreferencesKey("review_prompts")
    private val reviewLastAtKey = longPreferencesKey("review_last_at")
    private val reviewDueKey = booleanPreferencesKey("review_due")
    private val updateDeclinedAtKey = longPreferencesKey("update_declined_at")

    val viewPrefs: Flow<ViewPrefs> = context.dataStore.data.map { prefs ->
        ViewPrefs(
            sortBy = SortBy.entries.firstOrNull { it.name == prefs[sortByKey] } ?: SortBy.NAME,
            descending = prefs[descendingKey] ?: false,
            showHidden = prefs[showHiddenKey] ?: false,
            grid = prefs[gridKey] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setSort(by: SortBy, descending: Boolean) {
        context.dataStore.edit {
            it[sortByKey] = by.name
            it[descendingKey] = descending
        }
    }

    suspend fun setShowHidden(show: Boolean) {
        context.dataStore.edit { it[showHiddenKey] = show }
    }

    suspend fun setGrid(grid: Boolean) {
        context.dataStore.edit { it[gridKey] = grid }
    }

    /** Deleted files go to the Bin first. On unless turned off. */
    val useBin: Flow<Boolean> = context.dataStore.data.map { it[useBinKey] ?: true }.distinctUntilChanged()

    suspend fun setUseBin(use: Boolean) {
        context.dataStore.edit { it[useBinKey] = use }
    }

    /** Whether a category's list shows as a grid; follows the general choice until set for that category. */
    fun categoryGrid(category: String): Flow<Boolean> = context.dataStore.data
        .map { it[booleanPreferencesKey("grid_$category")] ?: it[gridKey] ?: false }.distinctUntilChanged()

    suspend fun setCategoryGrid(category: String, grid: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("grid_$category")] = grid }
    }

    /** How the photos or the videos are shown; each keeps its own. */
    fun galleryPrefs(category: String): Flow<GalleryPrefs> = context.dataStore.data.map { prefs ->
        GalleryPrefs(
            columns = Gallery.columns(prefs[intPreferencesKey("gallery_columns_$category")] ?: Gallery.DEFAULT_COLUMNS),
            sort = GallerySort.entries.firstOrNull { it.name == prefs[stringPreferencesKey("gallery_sort_$category")] } ?: GallerySort.NEWEST,
            list = prefs[booleanPreferencesKey("gallery_list_$category")] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setGalleryColumns(category: String, columns: Int) {
        context.dataStore.edit { it[intPreferencesKey("gallery_columns_$category")] = Gallery.columns(columns) }
    }

    suspend fun setGallerySort(category: String, sort: GallerySort) {
        context.dataStore.edit { it[stringPreferencesKey("gallery_sort_$category")] = sort.name }
    }

    suspend fun setGalleryList(category: String, list: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("gallery_list_$category")] = list }
    }

    /** True once, the first time something asks to show a notification. */
    suspend fun takeNotificationAsk(): Boolean {
        var first = false
        context.dataStore.edit {
            first = it[notificationAskedKey] != true
            it[notificationAskedKey] = true
        }
        return first
    }

    /** Folder paths pinned to the home screen. */
    val bookmarks: Flow<Set<String>> = context.dataStore.data.map { it[bookmarksKey] ?: emptySet() }.distinctUntilChanged()

    suspend fun setBookmarked(path: String, bookmarked: Boolean) {
        context.dataStore.edit {
            val current = it[bookmarksKey] ?: emptySet()
            it[bookmarksKey] = if (bookmarked) current + path else current - path
        }
    }

    /**
     * Counts a good moment (a copy, move, zip or extract that finished). When [ReviewPolicy] allows, marks a Play
     * review as due; the home screen asks once the person is back there, never in the middle of a task.
     */
    suspend fun recordGoodMoment(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            val moments = (it[goodMomentsKey] ?: 0) + 1
            it[goodMomentsKey] = moments
            if (ReviewPolicy.shouldAsk(moments, it[reviewPromptsKey] ?: 0, it[reviewLastAtKey] ?: 0L, nowMs)) it[reviewDueKey] = true
        }
    }

    /** True once when a review is due; records the prompt so it isn't asked again too soon. */
    suspend fun takeReviewDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        var due = false
        context.dataStore.edit {
            due = it[reviewDueKey] == true
            if (due) {
                it[reviewDueKey] = false
                it[reviewPromptsKey] = (it[reviewPromptsKey] ?: 0) + 1
                it[reviewLastAtKey] = nowMs
            }
        }
        return due
    }

    suspend fun updateDeclinedAt(): Long = context.dataStore.data.first()[updateDeclinedAtKey] ?: 0L

    suspend fun setUpdateDeclinedAt(nowMs: Long) {
        context.dataStore.edit { it[updateDeclinedAtKey] = nowMs }
    }
}
