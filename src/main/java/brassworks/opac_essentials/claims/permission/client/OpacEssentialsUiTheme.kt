package brassworks.opac_essentials.claims.permission.client

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.kit.base.BrassWidget
import java.awt.Color

object OpacEssentialsUiTheme {
    const val THEME_ID = "dark"
    const val ACCENT_HEX = "#6CC4F1"

    val BACKDROP = Color(0, 0, 0, 72)

    fun apply() {
        BrassThemes.apply(THEME_ID, ACCENT_HEX)
    }

    fun disableAnimations(component: UIComponent) {
        if (component is BrassWidget) component.entranceEnabled = false
        component.children.forEach(::disableAnimations)
    }
}
