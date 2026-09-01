package link.mczihan.androidResourceDownload.core.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 管理员临时预览"普通用户视角"的开关（仅内存态，不写后端、不写会话文件）。
 *
 * 管理员在设置页点击角色标记可切换开启/关闭：
 * - 开启后 UI 按普通用户权限显示（隐藏上传、复选、拖拽上传、更多操作等管理员功能）；
 * - 再次切换或重启应用即恢复管理员视角。
 * 普通用户不受影响（该开关对普通用户不生效，isAdmin 判断仍为 false）。
 */
object RolePreview {
    var asUser by mutableStateOf(false)
        private set

    /** 切换预览状态，返回切换后的状态（true=预览普通用户视角）。 */
    fun toggle(): Boolean {
        asUser = !asUser
        return asUser
    }
}
