/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.FactCheck",
    ReplaceWith( "Icons.AutoMirrored.Filled.FactCheck",
            "androidx.compose.material.icons.automirrored.filled.FactCheck"),
)
public val Icons.Filled.FactCheck: ImageVector
    get() {
        if (_factCheck != null) {
            return _factCheck!!
        }
        _factCheck = materialIcon(name = "Filled.FactCheck") {
            materialPath(pathFillType = EvenOdd) {
                moveTo(20.0f, 3.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 3.0f, 2.0f, 3.9f, 2.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveTo(22.0f, 3.9f, 21.1f, 3.0f, 20.0f, 3.0f)
                close()
                moveTo(10.0f, 17.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(10.0f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(13.0f)
                close()
                moveTo(10.0f, 9.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(14.82f, 15.0f)
                lineTo(12.0f, 12.16f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(1.41f, 1.42f)
                lineTo(17.99f, 9.0f)
                lineToRelative(1.42f, 1.42f)
                lineTo(14.82f, 15.0f)
                close()
            }
        }
        return _factCheck!!
    }

private var _factCheck: ImageVector? = null
