/*
 * Copyright (C) 2026 meerkat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.meerkat.heartrate2pebble.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "device_prefs")

class DeviceRepository(private val context: Context) {
    private val KEY_ADDRESS = stringPreferencesKey("device_address")
    private val KEY_NAME = stringPreferencesKey("device_name")
    private val KEY_TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")

    suspend fun saveDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADDRESS] = address
            prefs[KEY_NAME] = name
        }
    }

    suspend fun getSavedDevice(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val address = prefs[KEY_ADDRESS] ?: return null
        val name = prefs[KEY_NAME] ?: "Unknown"
        return address to name
    }

    suspend fun clearDevice() {
        context.dataStore.edit {
            it.remove(KEY_ADDRESS)
            it.remove(KEY_NAME)
        }
    }

    suspend fun hasAcceptedTerms(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_TERMS_ACCEPTED] == true
    }

    suspend fun setTermsAccepted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_TERMS_ACCEPTED] = true
        }
    }
}