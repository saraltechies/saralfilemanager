package com.saraltechies.saralfilemanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

/**
 * Selected paths, kept through a turn of the phone. Thousands of paths are too much for Android's saved screen state
 * (it would crash the app in the background), so only a short token is saved there and the paths stay in memory.
 * If Android closes the app altogether, the selection goes with it, which is the safe way round.
 */
private object SelectionStore {
    private const val KEEP = 8
    private val sets = object : LinkedHashMap<String, Set<String>>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>) = size > KEEP
    }

    @Synchronized
    fun put(paths: Set<String>): String = UUID.randomUUID().toString().also { sets[it] = paths }

    @Synchronized
    fun get(token: String): Set<String> = sets[token] ?: emptySet()
}

private val SelectionSaver = Saver<MutableState<Set<String>>, String>(
    save = { SelectionStore.put(it.value) },
    restore = { mutableStateOf(SelectionStore.get(it)) },
)

@Composable
fun rememberSelection(): MutableState<Set<String>> = rememberSaveable(saver = SelectionSaver) { mutableStateOf(emptySet()) }
