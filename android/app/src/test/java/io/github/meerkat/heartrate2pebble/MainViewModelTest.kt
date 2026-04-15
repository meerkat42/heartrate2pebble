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
package io.github.meerkat.heartrate2pebble

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.github.meerkat.heartrate2pebble.data.ble.BleManager
import io.github.meerkat.heartrate2pebble.data.local.DeviceRepository
import io.github.meerkat.heartrate2pebble.data.pebble.PebbleManager
import io.github.meerkat.heartrate2pebble.service.HeartRateService
import io.github.meerkat.heartrate2pebble.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var appContext: Context
    private lateinit var bleManager: BleManager
    private lateinit var pebbleManager: PebbleManager
    private lateinit var repository: DeviceRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        appContext = mockk(relaxed = true)
        bleManager = mockk(relaxed = true)
        pebbleManager = mockk(relaxed = true)
        repository = mockk(relaxed = true)

        every { bleManager.heartRate } returns MutableStateFlow("--")
        every { bleManager.isConnected } returns MutableStateFlow(false)
        every { bleManager.scanStatus } returns MutableStateFlow("")
        every { pebbleManager.pebbleConnected } returns MutableStateFlow(false)
        coEvery { repository.hasAcceptedTerms() } returns true

        mockkObject(HeartRateService.Companion)
        every { HeartRateService.isRunning } returns MutableStateFlow(false)

        viewModel = MainViewModel(appContext, bleManager, pebbleManager, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(HeartRateService.Companion)
    }

    @Test
    fun `initial state has no device paired`() {
        assertFalse(viewModel.isPaired)
        assertEquals("", viewModel.deviceName)
    }

    @Test
    fun `initial state has Pebble disabled`() {
        assertFalse(viewModel.isPebbleEnabled)
    }

    @Test
    fun `initial state hides picker`() {
        assertFalse(viewModel.showPicker)
    }

    @Test
    fun `terms accepted when repository returns true`() = runTest {
        assertTrue(viewModel.termsAccepted)
        assertFalse(viewModel.showTermsDialog)
    }

    @Test
    fun `terms dialog shown when repository returns false`() = runTest {
        coEvery { repository.hasAcceptedTerms() } returns false
        val vm = MainViewModel(appContext, bleManager, pebbleManager, repository)
        assertTrue(vm.showTermsDialog)
        assertFalse(vm.termsAccepted)
    }

    @Test
    fun `acceptTerms saves and updates state`() = runTest {
        coEvery { repository.hasAcceptedTerms() } returns false
        val vm = MainViewModel(appContext, bleManager, pebbleManager, repository)

        vm.acceptTerms()

        assertTrue(vm.termsAccepted)
        assertFalse(vm.showTermsDialog)
        coVerify { repository.setTermsAccepted() }
    }

    @Test
    fun `declineTerms disables but does not save`() = runTest {
        coEvery { repository.hasAcceptedTerms() } returns false
        val vm = MainViewModel(appContext, bleManager, pebbleManager, repository)

        vm.declineTerms()

        assertFalse(vm.termsAccepted)
        assertFalse(vm.showTermsDialog)
        coVerify(exactly = 0) { repository.setTermsAccepted() }
    }

    @Test
    fun `togglePebble updates state and syncs to service`() {
        viewModel.togglePebble(true)

        assertTrue(viewModel.isPebbleEnabled)
        assertTrue(HeartRateService.isPebbleEnabled)
    }

    @Test
    fun `unpair clears device state and repository`() = runTest {
        // Simulate a paired device
        coEvery { repository.getSavedDevice() } returns ("AA:BB:CC:DD:EE:FF" to "My HR Band")
        viewModel.loadSavedDevice()

        viewModel.unpair()

        assertEquals("", viewModel.deviceName)
        assertFalse(viewModel.isPaired)
        assertFalse(viewModel.isPebbleEnabled)
        coVerify { repository.clearDevice() }
    }

    @Test
    fun `stopSession disconnects BLE and disables Pebble`() = runTest {
        viewModel.togglePebble(true)

        viewModel.stopSession()

        assertFalse(viewModel.isPebbleEnabled)
        assertFalse(HeartRateService.isPebbleEnabled)
    }

    @Test
    fun `closePicker hides dialog and cancels scan`() {
        viewModel.closePicker()

        assertFalse(viewModel.showPicker)
    }

    @Test
    fun `loadSavedDevice sets name and paired state`() = runTest {
        coEvery { repository.getSavedDevice() } returns ("AA:BB:CC:DD:EE:FF" to "My HR Band")

        viewModel.loadSavedDevice()

        assertEquals("My HR Band", viewModel.deviceName)
        assertTrue(viewModel.isPaired)
    }

    @Test
    fun `loadSavedDevice does nothing when no saved device`() = runTest {
        coEvery { repository.getSavedDevice() } returns null

        viewModel.loadSavedDevice()

        assertEquals("", viewModel.deviceName)
        assertFalse(viewModel.isPaired)
    }
}
