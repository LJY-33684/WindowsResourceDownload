package link.mczihan.androidResourceDownload.core.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Global drag-and-drop state for desktop. The AWT DropTarget is installed on the
 * main window in Main.kt; FilesScreen observes [isDragOver] and registers
 * [onFileDrop] when the user is an admin.
 */
object DesktopDragDrop {
    var isDragOver by mutableStateOf(false)
    var onFilesDrop: ((List<File>) -> Unit)? = null
    var enabled: Boolean = false
}
