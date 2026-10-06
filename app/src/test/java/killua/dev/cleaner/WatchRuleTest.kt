package killua.dev.cleaner

import killua.dev.cleaner.data.backend.WatchFileScript
import killua.dev.cleaner.data.toSpec
import killua.dev.cleaner.domain.model.WatchKind
import killua.dev.cleaner.domain.model.WatchRule
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.WatchCodec
import killua.dev.cleaner.ipc.WatchSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRuleTest {

    private fun rule(kind: WatchKind, name: String, directory: String = "Download") = WatchRule(
        id = "rule-1",
        userId = 0,
        directory = directory,
        kind = kind,
        name = name,
        recursive = true,
        enabled = true,
        createdAt = 0,
    )

    @Test
    fun `codec round-trips every kind`() {
        val specs = listOf(
            rule(WatchKind.Folder, "tencent").toSpec(),
            rule(WatchKind.File, "*.apk").toSpec().copy(id = "rule-2", recursive = false),
            rule(WatchKind.Everything, "", directory = "").toSpec().copy(id = "rule-3", enabled = false),
        )
        assertEquals(specs, WatchCodec.decode(WatchCodec.encode(specs)))
    }

    @Test
    fun `decoder drops malformed and escaping lines`() {
        val text = "ok\t1\t0\t1\tDownload\ta\n" +
            "bad id!\t1\t0\t1\tDownload\ta\n" +
            "up\t1\t0\t1\tDownload/../..\ta\n" +
            "slash\t2\t0\t1\tDownload\ta/b\n" +
            "kind\t9\t0\t1\tDownload\ta\n" +
            "short\t1\t0\n"
        assertEquals(listOf("ok"), WatchCodec.decode(text).map { it.id })
    }

    @Test
    fun `directory is normalized and must stay inside storage`() {
        assertEquals("Android/media", WatchCodec.normalizeDirectory(" /Android//media/ "))
        assertEquals("", WatchCodec.normalizeDirectory("/"))
        assertNull(WatchCodec.normalizeDirectory("a/../b"))
        assertNull(WatchCodec.normalizeDirectory("a\tb"))
    }

    @Test
    fun `names match as case-insensitive globs`() {
        val apk = WatchSpec("r", "Download", IpcConstants.WATCH_KIND_FILE, "*.apk", recursive = false, enabled = true)
        assertTrue(apk.matchesName("Update.APK"))
        assertFalse(apk.matchesName("update.apk.tmp"))

        val exact = WatchSpec("r", "", IpcConstants.WATCH_KIND_FOLDER, "tencent", recursive = false, enabled = true)
        assertTrue(exact.matchesName("Tencent"))
        assertFalse(exact.matchesName("tencent2"))

        val dotted = WatchSpec("r", "", IpcConstants.WATCH_KIND_FILE, "a.b?", recursive = false, enabled = true)
        assertTrue(dotted.matchesName("a.bc"))
        assertFalse(dotted.matchesName("axbc"))
    }

    @Test
    fun `watch file is replaced atomically`() {
        val script = WatchFileScript.write(10, listOf(rule(WatchKind.Folder, "x").toSpec()))
        val target = IpcConstants.watchFile(10)
        assertTrue(script.contains("rule-1\t1\t1\t1\tDownload\tx\n__CLEANER_WATCH_END__"))
        assertTrue(script.trimEnd().endsWith("mv -f '$target.tmp' '$target'"))
    }
}
