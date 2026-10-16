package dev.ujhhgtg.wekit.features.items.contacts

import android.view.MenuItem
import androidx.activity.ComponentActivity
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.net.models.protobuf.NearbyFriendProto
import dev.ujhhgtg.wekit.features.api.net.models.protobuf.WeProto
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.FeatureCategoryIds
import dev.ujhhgtg.wekit.utils.reflection.int
import java.util.LinkedList
import org.luckypray.dexkit.DexKitBridge

object AutoAddNearbyFriends : ClickableFeature(), IResolveDex {

    override val technicalId = "自动添加附近的人"
    override val nameRes = R.string.feature_auto_add_nearby_friends_name
    override val categoryIds = listOf(FeatureCategoryIds.CONTACTS_GROUPS)
    override val descriptionRes = R.string.feature_auto_add_nearby_friends_description

    /** 「附近的人」底部菜单的构建方法; 由 [resolveDex] 填充。 */
    private val methodCreateMenu by dexMethod()

    /** 「附近的人」菜单项点击回调; 由 [resolveDex] 填充。 */
    private val methodMenuOnClick by dexMethod()

    /**
     * 旧版「附近的人」底部菜单是两个目标共同的前提: 构建菜单的方法所在类同时带
     * "NearbyPersonUIC" 与 "showLiveBottomSheet create menu."。
     *
     * 8.0.79 起微信重构了该 UI: 这两个特征串、`NearbySayHiListUI.onMMMenuItemSelected`
     * 以及 `NearbyPersonFragment` 一并消失, 且没有任何等价的菜单挂载点。这属于跨版本的存在性
     * 差异, 因此探测不到旧结构时把两个目标记为预期失败, 功能在该宿主上退化为 no-op; 只要旧结构
     * 还在, 两个目标就继续严格解析, 将来的失效仍会响亮报错。
     */
    override fun resolveDex(dexKit: DexKitBridge) {
        val legacyNearbyMenu = dexKit.findClass {
            matcher { usingEqStrings("NearbyPersonUIC", "showLiveBottomSheet create menu.") }
        }
        when (legacyNearbyMenu.size) {
            0 -> {
                val reason = "the nearby-people live bottom sheet menu was removed in 8.0.79"
                methodCreateMenu.setPlaceholderDescriptor(true, reason)
                methodMenuOnClick.setPlaceholderDescriptor(true, reason)
                return
            }
            1 -> Unit
            else -> error(
                "multiple near-person menu classes found: " +
                    legacyNearbyMenu.joinToString { it.name }
            )
        }

        methodCreateMenu.find(dexKit) {
            matcher { usingEqStrings("NearbyPersonUIC", "showLiveBottomSheet create menu.") }
        }

        methodMenuOnClick.find(dexKit) {
            matcher {
                usingEqStrings("com.tencent.mm.plugin.nearby.ui.NearbySayHiListUI")
                name = "onMMMenuItemSelected"
            }
        }
    }

    override fun onEnable() {
        if (methodCreateMenu.isPlaceholder) return

        methodCreateMenu.hookBefore {
            args[0]!!.reflekt().firstMethod {
                parameters(int, CharSequence::class)
            }.invoke(6, localizedContactsString(R.string.contacts_auto_add_nearby_menu))
        }

        methodMenuOnClick.hookBefore {
            val menuItem = args[0] as MenuItem
            val itemId = menuItem.itemId
            if (itemId != 6) return@hookBefore

            val controller = thisObject!!.reflekt().firstField().get()!!
            val friends = controller.reflekt().firstField {
                type = List::class
            }.get()!! as LinkedList<*>

            val friendProtos = friends.map {
                WeProto.decode<NearbyFriendProto>(
                    it.reflekt().invokeMethod("toByteArray", superclass = true) as ByteArray
                )
            }

            result = null
        }
    }

    override fun onClick(context: ComponentActivity) {

    }
}
