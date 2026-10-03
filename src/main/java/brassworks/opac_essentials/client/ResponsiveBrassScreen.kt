package brassworks.opac_essentials.client

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.universal.UResolution
import net.swzo.brass.ui.BrassScreen
import java.awt.Color

open class ResponsiveBrassScreen(
    private val windowWidth: Float,
    private val windowHeight: Float,
    private val horizontalMargin: Float,
    private val verticalMargin: Float,
    backdropColor: Color,
) : BrassScreen(backdropColor = backdropColor) {
    private var maximumGuiScale = 0
    private var scaleAdjusted = false

    override fun updateGuiScale() {
        if (maximumGuiScale == 0) {
            maximumGuiScale = UResolution.scaleFactor.toInt().coerceAtLeast(1)
        }
        val fittingScale = minOf(
            UResolution.viewportWidth / (windowWidth + horizontalMargin).toInt(),
            UResolution.viewportHeight / (windowHeight + verticalMargin).toInt(),
        ).coerceAtLeast(1)
        val targetScale = minOf(maximumGuiScale, fittingScale)
        newGuiScale = if (targetScale < maximumGuiScale || scaleAdjusted) {
            scaleAdjusted = true
            targetScale
        } else {
            -1
        }
        super.updateGuiScale()
    }

    override fun onScreenClose() {
        maximumGuiScale = 0
        scaleAdjusted = false
        newGuiScale = -1
        super.onScreenClose()
    }

    protected fun responsiveWindowWidthConstraint(): WidthConstraint =
        basicWidthConstraint { component -> windowWidth * windowScale(component) }

    protected fun responsiveWindowHeightConstraint(): HeightConstraint =
        basicHeightConstraint { component -> windowHeight * windowScale(component) }

    private fun windowScale(component: UIComponent): Float = minOf(
        1f,
        ((component.parent.getWidth() - horizontalMargin) / windowWidth).coerceAtLeast(0f),
        ((component.parent.getHeight() - verticalMargin) / windowHeight).coerceAtLeast(0f),
    )
}
