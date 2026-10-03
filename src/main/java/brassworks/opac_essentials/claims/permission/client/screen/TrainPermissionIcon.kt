package brassworks.opac_essentials.claims.permission.client.screen

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import net.swzo.brass.ui.Colors
import java.awt.Color

class TrainPermissionIcon : UIContainer() {
    init {
        part(10f, 78f, 80f, 5f, Colors.UI_TEXT_DARK)
        part(17f, 85f, 15f, 5f, Colors.UI_TEXT_DARK)
        part(43f, 85f, 15f, 5f, Colors.UI_TEXT_DARK)
        part(68f, 85f, 15f, 5f, Colors.UI_TEXT_DARK)
        part(15f, 44f, 64f, 30f, Colors.UI_ACCENT)
        part(54f, 24f, 25f, 50f, Colors.UI_ACCENT)
        part(60f, 31f, 12f, 14f, Colors.UI_INNER_BG)
        part(25f, 27f, 12f, 17f, Colors.UI_ACCENT)
        part(22f, 22f, 18f, 5f, Colors.UI_ACCENT_BRIGHT)
        part(8f, 61f, 12f, 13f, Colors.UI_ACCENT_BRIGHT)
        part(19f, 68f, 18f, 18f, Colors.UI_TEXT_DARK)
        part(57f, 68f, 18f, 18f, Colors.UI_TEXT_DARK)
        part(24f, 73f, 8f, 8f, Colors.UI_ACCENT_BRIGHT)
        part(62f, 73f, 8f, 8f, Colors.UI_ACCENT_BRIGHT)
    }

    private fun part(
        left: Float,
        top: Float,
        widthValue: Float,
        heightValue: Float,
        color: Color,
    ) {
        UIBlock(color).constrain {
            x = left.percent()
            y = top.percent()
            width = widthValue.percent()
            height = heightValue.percent()
        } childOf this
    }
}
