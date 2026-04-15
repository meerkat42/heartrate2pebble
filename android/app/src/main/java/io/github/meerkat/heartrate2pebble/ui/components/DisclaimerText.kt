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
package io.github.meerkat.heartrate2pebble.ui.components

val DISCLAIMER_TEXT = """
This software is for recreational/educational purposes only. It is NOT a medical product and has not been certified by any health authority and is not intended for medical use, diagnosis, or treatment.

The data provided by this app should not be used as a substitute for professional medical advice. Always consult with a qualified healthcare provider before beginning a new exercise program or if you have concerns about your heart health.

Heart rate readings may be inaccurate or delayed due to sensor limitations, transmission delays, Bluetooth interference, software latency, etc. Hardware/software compatibility cannot be guaranteed. Do not rely on this data for life-safety or medical monitoring. Use at your own risk. 
""".trimIndent()

const val DISCLAIMER_END_TEXT = "you acknowledge that this application is NOT for medical but recreational use, operates strictly locally without data collection, and that the developer is not liable for any health issues, injuries, or damages resulting from the use or misuse of this application."

val DISCLAIMER_POPUP_TEXT = DISCLAIMER_TEXT + """
    

By clicking 'Accept' and installing and using this software, """.trimIndent() + DISCLAIMER_END_TEXT

val DISCLAIMER_ABOUT_TEXT = DISCLAIMER_TEXT + """
    

By installing and using this software, """.trimIndent() + DISCLAIMER_END_TEXT
