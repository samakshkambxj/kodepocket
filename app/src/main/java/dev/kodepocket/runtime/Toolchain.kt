package dev.kodepocket.runtime

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * The bundled Termux toolchain: bash, busybox, git, ripgrep, curl and the
 * shared libraries they link against.
 *
 * The binaries ship as native libraries because that is the only app-owned
 * place Android lets us execute from. `nativeLibraryDir` is read-only and
 * names everything `lib*.so`, while the loader asks for sonames and git asks
 * for subcommands by name, so startup builds a small symlink farm in
 * app-private storage. Executing a symlink resolves to the native library, and
 * SELinux allows `execute_no_trans` on `apk_data_file` for app domains.
 */
class Toolchain(private val context: Context) {

    data class Manifest(
        val version: String,
        val bin: Map<String, String>,
        val lib: Map<String, String>,
        val gitExec: Map<String, String>,
        val applets: List<String>,
    )

    val root = File(context.filesDir, "toolchain")
    val binDir = File(root, "bin")
    val libDir = File(root, "lib")
    val gitExecDir = File(root, "libexec/git-core")
    val terminfoDir = File(root, "share/terminfo")
    val templateDir = File(root, "share/git-core/templates")
    val caBundle = File(root, "etc/tls/cert.pem")
    val bash: File get() = File(binDir, "bash")

    private val cachedManifest: Manifest by lazy { readManifest() }

    /**
     * Materializes the toolchain. Asset copies happen once per manifest
     * version; the symlink farm is rebuilt on every start because
     * `nativeLibraryDir` changes when the app updates.
     */
    fun prepare(): Manifest {
        val manifest = cachedManifest
        listOf(binDir, libDir, gitExecDir, terminfoDir, templateDir).forEach { it.mkdirs() }
        caBundle.parentFile?.mkdirs()
        installAssets(manifest)
        linkFarm(manifest, File(context.applicationInfo.nativeLibraryDir))
        return manifest
    }

    private fun installAssets(manifest: Manifest) {
        val stamp = File(root, "stamp")
        val current = runCatching { stamp.readText() }.getOrNull()
        if (current == manifest.version && caBundle.isFile) return

        copyAsset("toolchain/cert.pem", caBundle)
        copyTree("toolchain/terminfo", terminfoDir)
        copyTree("toolchain/git/templates", templateDir)
        stamp.writeText(manifest.version)
    }

    private fun linkFarm(manifest: Manifest, native: File) {
        for (entry in planFarm(manifest)) {
            val dir = when (entry.farm) {
                Farm.Bin -> binDir
                Farm.Lib -> libDir
                Farm.GitExec -> gitExecDir
            }
            link(File(dir, entry.name), File(native, entry.lib))
        }
    }

    private fun link(link: File, target: File) {
        if (runCatching { Os.readlink(link.path) }.getOrNull() == target.path) return
        runCatching { link.delete() }
        try {
            Os.symlink(target.path, link.path)
        } catch (e: ErrnoException) {
            throw IOException("Could not link ${link.path} -> ${target.path}", e)
        }
    }

    private fun copyTree(assetPath: String, dest: File) {
        dest.deleteRecursively()
        dest.mkdirs()
        copyChildren(assetPath, dest)
    }

    private fun copyChildren(assetPath: String, dest: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            copyAsset(assetPath, dest)
            return
        }
        for (child in children) {
            val childAsset = "$assetPath/$child"
            val childDest = File(dest, child)
            if (context.assets.list(childAsset).orEmpty().isEmpty()) {
                copyAsset(childAsset, childDest)
            } else {
                childDest.mkdirs()
                copyChildren(childAsset, childDest)
            }
        }
    }

    private fun copyAsset(assetPath: String, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setReadable(true, true)
    }

    private fun readManifest(): Manifest {
        val text = context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        return Manifest(
            version = json.getString("version"),
            bin = json.map("bin"),
            lib = json.map("lib"),
            gitExec = json.map("gitExec"),
            applets = json.getJSONArray("applets").let { array ->
                List(array.length()) { array.getString(it) }
            },
        )
    }

    private fun JSONObject.map(key: String): Map<String, String> {
        val source = getJSONObject(key)
        val result = mutableMapOf<String, String>()
        val names = source.keys()
        while (names.hasNext()) {
            val name = names.next()
            result[name] = source.getString(name)
        }
        return result
    }

    companion object {
        private const val MANIFEST = "toolchain/manifest.json"
    }
}

/** Which symlink farm an entry belongs to. */
internal enum class Farm { Bin, Lib, GitExec }

internal data class FarmLink(val farm: Farm, val name: String, val lib: String)

/**
 * Decides what to link where. Kept free of Android APIs so the precedence rules
 * — a real tool beats a busybox applet of the same name — can be unit tested.
 */
internal fun planFarm(manifest: Toolchain.Manifest): List<FarmLink> = buildList {
    manifest.lib.forEach { (soname, lib) -> add(FarmLink(Farm.Lib, soname, lib)) }

    val busybox = manifest.bin["busybox"]
    if (busybox != null) {
        manifest.applets.forEach { applet ->
            if (!manifest.bin.containsKey(applet)) add(FarmLink(Farm.Bin, applet, busybox))
        }
    }
    manifest.bin.forEach { (name, lib) -> add(FarmLink(Farm.Bin, name, lib)) }
    manifest.gitExec.forEach { (name, lib) -> add(FarmLink(Farm.GitExec, name, lib)) }
}
