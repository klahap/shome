package de.quati.shome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

const val UNNAMED_SHELLY = "Unnamed Shelly"

data class RolloStyle(
    val progressBarColor: Color,
    val rolloBarColor: Color,
) {
    fun draw(scope: DrawScope, progress: Float) = with(scope) {
        drawRect(
            color = progressBarColor,
            size = Size(size.width, size.height * progress)
        )
        var y = progress
        while (y > 0f) {
            drawLine(
                color = rolloBarColor,
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * y)
            )
            y -= 0.05f
        }
    }
}