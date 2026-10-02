/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Copied from material-icons-extended so the app can depend on material-icons-core only:
// the extended artifact adds ~13k icon classes (~40 MB of dex), which makes unminified
// cold starts slow enough to ANR on low-end TVs.
package com.mobplayer.tv.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.Filled.ArrowDownward: ImageVector
    get() {
        if (_arrowDownward != null) {
            return _arrowDownward!!
        }
        _arrowDownward = materialIcon(name = "Filled.ArrowDownward") {
            materialPath {
                moveTo(20.0f, 12.0f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(13.0f, 16.17f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(12.17f)
                lineToRelative(-5.58f, -5.59f)
                lineTo(4.0f, 12.0f)
                lineToRelative(8.0f, 8.0f)
                lineToRelative(8.0f, -8.0f)
                close()
            }
        }
        return _arrowDownward!!
    }

private var _arrowDownward: ImageVector? = null

val Icons.Filled.ArrowUpward: ImageVector
    get() {
        if (_arrowUpward != null) {
            return _arrowUpward!!
        }
        _arrowUpward = materialIcon(name = "Filled.ArrowUpward") {
            materialPath {
                moveTo(4.0f, 12.0f)
                lineToRelative(1.41f, 1.41f)
                lineTo(11.0f, 7.83f)
                verticalLineTo(20.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.83f)
                lineToRelative(5.58f, 5.59f)
                lineTo(20.0f, 12.0f)
                lineToRelative(-8.0f, -8.0f)
                lineToRelative(-8.0f, 8.0f)
                close()
            }
        }
        return _arrowUpward!!
    }

private var _arrowUpward: ImageVector? = null

val Icons.Filled.CastConnected: ImageVector
    get() {
        if (_castConnected != null) {
            return _castConnected!!
        }
        _castConnected = materialIcon(name = "Filled.CastConnected") {
            materialPath {
                moveTo(1.0f, 18.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(1.0f, 14.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(19.0f, 7.0f)
                lineTo(5.0f, 7.0f)
                verticalLineToRelative(1.63f)
                curveToRelative(3.96f, 1.28f, 7.09f, 4.41f, 8.37f, 8.37f)
                lineTo(19.0f, 17.0f)
                lineTo(19.0f, 7.0f)
                close()
                moveTo(1.0f, 10.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, 4.03f, 9.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -6.08f, -4.93f, -11.0f, -11.0f, -11.0f)
                close()
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }
        return _castConnected!!
    }

private var _castConnected: ImageVector? = null

val Icons.Filled.FastForward: ImageVector
    get() {
        if (_fastForward != null) {
            return _fastForward!!
        }
        _fastForward = materialIcon(name = "Filled.FastForward") {
            materialPath {
                moveTo(4.0f, 18.0f)
                lineToRelative(8.5f, -6.0f)
                lineTo(4.0f, 6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(13.0f, 6.0f)
                verticalLineToRelative(12.0f)
                lineToRelative(8.5f, -6.0f)
                lineTo(13.0f, 6.0f)
                close()
            }
        }
        return _fastForward!!
    }

private var _fastForward: ImageVector? = null

val Icons.Filled.FastRewind: ImageVector
    get() {
        if (_fastRewind != null) {
            return _fastRewind!!
        }
        _fastRewind = materialIcon(name = "Filled.FastRewind") {
            materialPath {
                moveTo(11.0f, 18.0f)
                lineTo(11.0f, 6.0f)
                lineToRelative(-8.5f, 6.0f)
                lineToRelative(8.5f, 6.0f)
                close()
                moveTo(11.5f, 12.0f)
                lineToRelative(8.5f, 6.0f)
                lineTo(20.0f, 6.0f)
                lineToRelative(-8.5f, 6.0f)
                close()
            }
        }
        return _fastRewind!!
    }

private var _fastRewind: ImageVector? = null

val Icons.Filled.Forward10: ImageVector
    get() {
        if (_forward10 != null) {
            return _forward10!!
        }
        _forward10 = materialIcon(name = "Filled.Forward10") {
            materialPath {
                moveTo(18.0f, 13.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                verticalLineToRelative(4.0f)
                lineToRelative(5.0f, -5.0f)
                lineToRelative(-5.0f, -5.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                horizontalLineTo(18.0f)
                close()
            }
            materialPath {
                moveTo(10.86f, 15.94f)
                lineToRelative(0.0f, -4.27f)
                lineToRelative(-0.09f, 0.0f)
                lineToRelative(-1.77f, 0.63f)
                lineToRelative(0.0f, 0.69f)
                lineToRelative(1.01f, -0.31f)
                lineToRelative(0.0f, 3.26f)
                close()
            }
            materialPath {
                moveTo(12.25f, 13.44f)
                verticalLineToRelative(0.74f)
                curveToRelative(0.0f, 1.9f, 1.31f, 1.82f, 1.44f, 1.82f)
                curveToRelative(0.14f, 0.0f, 1.44f, 0.09f, 1.44f, -1.82f)
                verticalLineToRelative(-0.74f)
                curveToRelative(0.0f, -1.9f, -1.31f, -1.82f, -1.44f, -1.82f)
                curveTo(13.55f, 11.62f, 12.25f, 11.53f, 12.25f, 13.44f)
                close()
                moveTo(14.29f, 13.32f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.77f, -0.21f, 1.03f, -0.59f, 1.03f)
                curveToRelative(-0.38f, 0.0f, -0.6f, -0.26f, -0.6f, -1.03f)
                verticalLineToRelative(-0.97f)
                curveToRelative(0.0f, -0.75f, 0.22f, -1.01f, 0.59f, -1.01f)
                curveTo(14.07f, 12.3f, 14.29f, 12.57f, 14.29f, 13.32f)
                close()
            }
        }
        return _forward10!!
    }

private var _forward10: ImageVector? = null

val Icons.Filled.Movie: ImageVector
    get() {
        if (_movie != null) {
            return _movie!!
        }
        _movie = materialIcon(name = "Filled.Movie") {
            materialPath {
                moveTo(18.0f, 4.0f)
                lineToRelative(2.0f, 4.0f)
                horizontalLineToRelative(-3.0f)
                lineToRelative(-2.0f, -4.0f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(2.0f, 4.0f)
                horizontalLineToRelative(-3.0f)
                lineToRelative(-2.0f, -4.0f)
                horizontalLineTo(8.0f)
                lineToRelative(2.0f, 4.0f)
                horizontalLineTo(7.0f)
                lineTo(5.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }
        return _movie!!
    }

private var _movie: ImageVector? = null

val Icons.Filled.Pause: ImageVector
    get() {
        if (_pause != null) {
            return _pause!!
        }
        _pause = materialIcon(name = "Filled.Pause") {
            materialPath {
                moveTo(6.0f, 19.0f)
                horizontalLineToRelative(4.0f)
                lineTo(10.0f, 5.0f)
                lineTo(6.0f, 5.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(14.0f, 5.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(4.0f)
                lineTo(18.0f, 5.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }
        return _pause!!
    }

private var _pause: ImageVector? = null

val Icons.Filled.Replay10: ImageVector
    get() {
        if (_replay10 != null) {
            return _replay10!!
        }
        _replay10 = materialIcon(name = "Filled.Replay10") {
            materialPath {
                moveTo(11.99f, 5.0f)
                verticalLineTo(1.0f)
                lineToRelative(-5.0f, 5.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                reflectiveCurveTo(16.41f, 5.0f, 11.99f, 5.0f)
                close()
            }
            materialPath {
                moveTo(10.89f, 16.0f)
                horizontalLineToRelative(-0.85f)
                verticalLineToRelative(-3.26f)
                lineToRelative(-1.01f, 0.31f)
                verticalLineToRelative(-0.69f)
                lineToRelative(1.77f, -0.63f)
                horizontalLineToRelative(0.09f)
                verticalLineTo(16.0f)
                close()
            }
            materialPath {
                moveTo(15.17f, 14.24f)
                curveToRelative(0.0f, 0.32f, -0.03f, 0.6f, -0.1f, 0.82f)
                reflectiveCurveToRelative(-0.17f, 0.42f, -0.29f, 0.57f)
                reflectiveCurveToRelative(-0.28f, 0.26f, -0.45f, 0.33f)
                reflectiveCurveToRelative(-0.37f, 0.1f, -0.59f, 0.1f)
                reflectiveCurveToRelative(-0.41f, -0.03f, -0.59f, -0.1f)
                reflectiveCurveToRelative(-0.33f, -0.18f, -0.46f, -0.33f)
                reflectiveCurveToRelative(-0.23f, -0.34f, -0.3f, -0.57f)
                reflectiveCurveToRelative(-0.11f, -0.5f, -0.11f, -0.82f)
                verticalLineTo(13.5f)
                curveToRelative(0.0f, -0.32f, 0.03f, -0.6f, 0.1f, -0.82f)
                reflectiveCurveToRelative(0.17f, -0.42f, 0.29f, -0.57f)
                reflectiveCurveToRelative(0.28f, -0.26f, 0.45f, -0.33f)
                reflectiveCurveToRelative(0.37f, -0.1f, 0.59f, -0.1f)
                reflectiveCurveToRelative(0.41f, 0.03f, 0.59f, 0.1f)
                curveToRelative(0.18f, 0.07f, 0.33f, 0.18f, 0.46f, 0.33f)
                reflectiveCurveToRelative(0.23f, 0.34f, 0.3f, 0.57f)
                reflectiveCurveToRelative(0.11f, 0.5f, 0.11f, 0.82f)
                verticalLineTo(14.24f)
                close()
                moveTo(14.32f, 13.38f)
                curveToRelative(0.0f, -0.19f, -0.01f, -0.35f, -0.04f, -0.48f)
                reflectiveCurveToRelative(-0.07f, -0.23f, -0.12f, -0.31f)
                reflectiveCurveToRelative(-0.11f, -0.14f, -0.19f, -0.17f)
                reflectiveCurveToRelative(-0.16f, -0.05f, -0.25f, -0.05f)
                reflectiveCurveToRelative(-0.18f, 0.02f, -0.25f, 0.05f)
                reflectiveCurveToRelative(-0.14f, 0.09f, -0.19f, 0.17f)
                reflectiveCurveToRelative(-0.09f, 0.18f, -0.12f, 0.31f)
                reflectiveCurveToRelative(-0.04f, 0.29f, -0.04f, 0.48f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.19f, 0.01f, 0.35f, 0.04f, 0.48f)
                reflectiveCurveToRelative(0.07f, 0.24f, 0.12f, 0.32f)
                reflectiveCurveToRelative(0.11f, 0.14f, 0.19f, 0.17f)
                reflectiveCurveToRelative(0.16f, 0.05f, 0.25f, 0.05f)
                reflectiveCurveToRelative(0.18f, -0.02f, 0.25f, -0.05f)
                reflectiveCurveToRelative(0.14f, -0.09f, 0.19f, -0.17f)
                reflectiveCurveToRelative(0.09f, -0.19f, 0.11f, -0.32f)
                reflectiveCurveToRelative(0.04f, -0.29f, 0.04f, -0.48f)
                verticalLineTo(13.38f)
                close()
            }
        }
        return _replay10!!
    }

private var _replay10: ImageVector? = null

val Icons.Filled.Tv: ImageVector
    get() {
        if (_tv != null) {
            return _tv!!
        }
        _tv = materialIcon(name = "Filled.Tv") {
            materialPath {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 17.0f)
                lineTo(3.0f, 17.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(12.0f)
                close()
            }
        }
        return _tv!!
    }

private var _tv: ImageVector? = null

val Icons.AutoMirrored.Filled.VolumeDown: ImageVector
    get() {
        if (_volumeDown != null) {
            return _volumeDown!!
        }
        _volumeDown = materialIcon(name = "AutoMirrored.Filled.VolumeDown", autoMirror = true) {
            materialPath {
                moveTo(18.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(5.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(4.0f)
                lineTo(9.0f, 9.0f)
                horizontalLineTo(5.0f)
                close()
            }
        }
        return _volumeDown!!
    }

private var _volumeDown: ImageVector? = null

val Icons.AutoMirrored.Filled.VolumeOff: ImageVector
    get() {
        if (_volumeOff != null) {
            return _volumeOff!!
        }
        _volumeOff = materialIcon(name = "AutoMirrored.Filled.VolumeOff", autoMirror = true) {
            materialPath {
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(2.21f)
                lineToRelative(2.45f, 2.45f)
                curveToRelative(0.03f, -0.2f, 0.05f, -0.41f, 0.05f, -0.63f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(0.0f, 0.94f, -0.2f, 1.82f, -0.54f, 2.64f)
                lineToRelative(1.51f, 1.51f)
                curveTo(20.63f, 14.91f, 21.0f, 13.5f, 21.0f, 12.0f)
                curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                close()
                moveTo(4.27f, 3.0f)
                lineTo(3.0f, 4.27f)
                lineTo(7.73f, 9.0f)
                lineTo(3.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineToRelative(-6.73f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
                verticalLineToRelative(2.06f)
                curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
                lineTo(19.73f, 21.0f)
                lineTo(21.0f, 19.73f)
                lineToRelative(-9.0f, -9.0f)
                lineTo(4.27f, 3.0f)
                close()
                moveTo(12.0f, 4.0f)
                lineTo(9.91f, 6.09f)
                lineTo(12.0f, 8.18f)
                lineTo(12.0f, 4.0f)
                close()
            }
        }
        return _volumeOff!!
    }

private var _volumeOff: ImageVector? = null

val Icons.AutoMirrored.Filled.VolumeUp: ImageVector
    get() {
        if (_volumeUp != null) {
            return _volumeUp!!
        }
        _volumeUp = materialIcon(name = "AutoMirrored.Filled.VolumeUp", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                lineTo(12.0f, 4.0f)
                lineTo(7.0f, 9.0f)
                lineTo(3.0f, 9.0f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14.0f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5.0f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7.0f, -4.49f, 7.0f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7.0f, -8.77f)
                close()
            }
        }
        return _volumeUp!!
    }

private var _volumeUp: ImageVector? = null

val Icons.Filled.Wifi: ImageVector
    get() {
        if (_wifi != null) {
            return _wifi!!
        }
        _wifi = materialIcon(name = "Filled.Wifi") {
            materialPath {
                moveTo(1.0f, 9.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1.0f, 9.0f)
                close()
                moveTo(9.0f, 17.0f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6.0f, 0.0f)
                close()
                moveTo(5.0f, 13.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5.0f, 13.0f)
                close()
            }
        }
        return _wifi!!
    }

private var _wifi: ImageVector? = null
