package link.mczihan.androidResourceDownload.core.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.CountDownLatch

/**
 * Windows 10/11 native file open dialog (IFileOpenDialog) via JNA.
 *
 * Replaces the legacy Swing JFileChooser, which renders the old Windows XP
 * style dialog. IFileOpenDialog is the native modern dialog used by Win10/11
 * Explorer ("打开" / "选择文件夹").
 *
 * The dialog runs on a dedicated STA thread (CoInitializeEx per thread) and
 * blocks the caller until the user picks or cancels.
 */
object NativeFileDialog {

    private val CLSID_FILE_OPEN_DIALOG = Guid.CLSID.fromString("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")
    private val IID_IFILE_OPEN_DIALOG = Guid.IID.fromString("D57C7288-D4AD-4768-BE02-9D969532D960")

    // FOS (File Open Options) flags
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_FILEMUSTEXIST = 0x1000
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val FOS_ALLOWMULTISELECT = 0x200
    private const val CLSCTX_INPROC_SERVER = 1
    private const val FOS_PICKFOLDERS = 0x20
    private val SIGDN_FILESYSPATH = 0x80058000L

    // COM vtable offsets
    private const val VT_SHOW = 3 // IModalWindow::Show(HWND)
    private const val VT_SETOPTIONS = 9 // IFileDialog::SetOptions(FOS)
    private const val VT_SETTITLE = 17 // IFileDialog::SetTitle(LPCWSTR)
    private const val VT_GETRESULT = 20 // IFileDialog::GetResult(IShellItem**)
    private const val VT_GETRESULTS = 21 // IFileOpenDialog::GetResults(IShellItemArray**)
    private const val VT_GETDISPLAYNAME = 2 // IShellItem::GetDisplayName(SIGDN, LPWSTR*)
    private const val VT_GETCOUNT = 3 // IShellItemArray::GetCount(DWORD*)
    private const val VT_GETITEMAT = 4 // IShellItemArray::GetItemAt(DWORD, IShellItem**)
    private const val VT_RELEASE = 2 // IUnknown::Release()

    private const val HRESULT_CANCELLED = 0x800704C7L // HRESULT_FROM_WIN32(ERROR_CANCELLED)

    /** Opens the native file picker. Returns absolute paths, empty on cancel/failure. */
    fun pickFiles(title: String, allowMultiple: Boolean): List<String> {
        return runDialog { dialog ->
            var options = FOS_FORCEFILESYSTEM or FOS_FILEMUSTEXIST or FOS_PATHMUSTEXIST
            if (allowMultiple) options = options or FOS_ALLOWMULTISELECT
            call(dialog, VT_SETOPTIONS, options)
            if (title.isNotBlank()) call(dialog, VT_SETTITLE, WString(title))
            val hr = call(dialog, VT_SHOW, 0L)
            if (hr == HRESULT_CANCELLED.toInt()) return@runDialog emptyList()
            if (hr < 0) return@runDialog emptyList()

            val out = mutableListOf<String>()
            if (allowMultiple) {
                val pArray = Memory(Native.POINTER_SIZE.toLong())
                if (call(dialog, VT_GETRESULTS, Pointer.nativeValue(pArray)) != 0) return@runDialog emptyList()
                val array = pArray.getPointer(0)
                try {
                    val countMem = Memory(4L)
                    if (call(array, VT_GETCOUNT, Pointer.nativeValue(countMem)) != 0) return@runDialog emptyList()
                    val count = countMem.getInt(0)
                    for (i in 0 until count) {
                        val pItem = Memory(Native.POINTER_SIZE.toLong())
                        if (call(array, VT_GETITEMAT, i.toLong(), Pointer.nativeValue(pItem)) != 0) continue
                        val item = pItem.getPointer(0)
                        try {
                            val path = displayName(item)
                            if (path != null) out.add(path)
                        } finally {
                            call(item, VT_RELEASE, 0)
                        }
                    }
                } finally {
                    call(array, VT_RELEASE, 0)
                }
            } else {
                val pItem = Memory(Native.POINTER_SIZE.toLong())
                if (call(dialog, VT_GETRESULT, Pointer.nativeValue(pItem)) != 0) return@runDialog emptyList()
                val item = pItem.getPointer(0)
                try {
                    val path = displayName(item)
                    if (path != null) out.add(path)
                } finally {
                    call(item, VT_RELEASE, 0)
                }
            }
            out
        }
    }

    /** Opens the native folder picker. Returns the absolute path or null on cancel/failure. */
    fun pickFolder(title: String): String? {
        return runDialog { dialog ->
            val options = FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST
            call(dialog, VT_SETOPTIONS, options)
            if (title.isNotBlank()) call(dialog, VT_SETTITLE, WString(title))
            val hr = call(dialog, VT_SHOW, 0L)
            if (hr == HRESULT_CANCELLED.toInt()) return@runDialog emptyList()
            if (hr < 0) return@runDialog emptyList()
            val pItem = Memory(Native.POINTER_SIZE.toLong())
            if (call(dialog, VT_GETRESULT, Pointer.nativeValue(pItem)) != 0) return@runDialog emptyList()
            val item = pItem.getPointer(0)
            try {
                val path = displayName(item)
                if (path != null) listOf(path) else emptyList()
            } finally {
                call(item, VT_RELEASE, 0)
            }
        }.firstOrNull()
    }

    private fun displayName(item: Pointer): String? {
        val pName = Memory(Native.POINTER_SIZE.toLong())
        if (call(item, VT_GETDISPLAYNAME, SIGDN_FILESYSPATH.toInt(), Pointer.nativeValue(pName)) != 0) return null
        val w = pName.getPointer(0)
        if (w == null || w == Pointer.NULL) return null
        return try {
            w.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(w)
        }
    }

    private fun runDialog(block: (Pointer) -> List<String>): List<String> {
        val result = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val thread = Thread {
            try {
                // Dedicated thread: no prior COM initialization, so CoInitializeEx returns S_OK.
                Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
                val ppv = PointerByReference()
                Ole32.INSTANCE.CoCreateInstance(
                    CLSID_FILE_OPEN_DIALOG,
                    null,
                    CLSCTX_INPROC_SERVER,
                    IID_IFILE_OPEN_DIALOG,
                    ppv,
                )
                val dialog = ppv.value
                if (dialog != null && dialog != Pointer.NULL) {
                    try {
                        result.addAll(block(dialog))
                    } finally {
                        call(dialog, VT_RELEASE, 0)
                    }
                }
            } catch (_: Throwable) {
                // COM failure -> fall back to empty result (caller can decide)
            } finally {
                Ole32.INSTANCE.CoUninitialize()
                latch.countDown()
            }
        }
        thread.isDaemon = true
        thread.start()
        latch.await()
        return result
    }

    private fun call(obj: Pointer, index: Int, vararg args: Any): Int {
        val vtbl = obj.getPointer(0)
        val fn = vtbl.getPointer(index.toLong() * Native.POINTER_SIZE.toLong())
        val f = Function.getFunction(fn)
        return f.invokeInt(arrayOf<Any>(obj, *args))
    }
}
