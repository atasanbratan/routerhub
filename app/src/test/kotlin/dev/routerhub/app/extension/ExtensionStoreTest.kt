package dev.routerhub.app.extension

import dev.routerhub.api.ExtensionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionStoreTest {
    @Test
    fun `a repository address is the index itself, or the folder it is in, over https only`() {
        assertEquals(ExtensionRepos.OFFICIAL, ExtensionRepos.normalize(" ${ExtensionRepos.OFFICIAL} "))
        assertEquals(
            "https://example.org/repo/index.min.json",
            ExtensionRepos.normalize("https://example.org/repo/"),
        )
        assertEquals("https://example.org/index.json", ExtensionRepos.normalize("https://example.org/index.json"))
        assertNull(ExtensionRepos.normalize("http://example.org/index.min.json"))
        assertNull(ExtensionRepos.normalize("example.org"))
        assertNull(ExtensionRepos.normalize(""))
    }

    @Test
    fun `an index lists its extensions with APK addresses relative to it`() {
        val index = """
            {"name":"RouterHub extensions","signingKey":"${"ab".repeat(32)}","extensions":[
              {"name":"Tenda AC","packageName":"dev.routerhub.extension.tenda","versionCode":3,"versionName":"1.2.0",
               "apiVersion":${ExtensionContract.API_VERSION},"apk":"apk/tenda.apk","size":225534,"sha256":"00"},
              {"name":"From the future","packageName":"dev.example.future","versionCode":1,"versionName":"9.0",
               "apiVersion":${ExtensionContract.API_VERSION + 1},"apk":"https://cdn.example.org/future.apk","size":1,"sha256":"11"},
              {"name":"Plain http","packageName":"dev.example.http","versionCode":1,"versionName":"1.0",
               "apiVersion":1,"apk":"http://example.org/http.apk","size":1,"sha256":"22"}
            ]}
        """.trimIndent()
        val (repo, extensions) = ExtensionStore.parseIndex(ExtensionRepos.OFFICIAL, index)
        assertEquals("RouterHub extensions", repo.name)
        assertEquals("ab".repeat(32), repo.signingKey)
        assertEquals(listOf("dev.routerhub.extension.tenda", "dev.example.future"), extensions.map { it.packageName })
        val tenda = extensions.first()
        assertEquals("https://raw.githubusercontent.com/atasanbratan/routerhub-extensions/repo/apk/tenda.apk", tenda.apkUrl)
        assertEquals(3L, tenda.versionCode)
        assertTrue(tenda.compatible)
        assertFalse(extensions[1].compatible)
    }

    @Test
    fun `a blocked raw GitHub address is read from jsDelivr, APKs included`() {
        val mirror = ExtensionStore.mirrorOf(ExtensionRepos.OFFICIAL)
        assertEquals("https://cdn.jsdelivr.net/gh/atasanbratan/routerhub-extensions@repo/index.min.json", mirror)
        assertNull(ExtensionStore.mirrorOf("https://example.org/index.min.json"))
        val index = """{"extensions":[{"name":"Tenda AC","packageName":"t","versionCode":1,"versionName":"1",
            "apiVersion":1,"apk":"apk/t.apk","size":1,"sha256":"00"}]}"""
        val (repo, extensions) = ExtensionStore.parseIndex(ExtensionRepos.OFFICIAL, index, base = mirror!!)
        assertEquals(ExtensionRepos.OFFICIAL, repo.url)
        assertEquals("https://cdn.jsdelivr.net/gh/atasanbratan/routerhub-extensions@repo/apk/t.apk", extensions.single().apkUrl)
    }

    @Test
    fun `an index without a name or key still reads`() {
        val (repo, extensions) = ExtensionStore.parseIndex("https://example.org/x/index.min.json", """{"extensions":[]}""")
        assertEquals("example.org", repo.name)
        assertNull(repo.signingKey)
        assertTrue(extensions.isEmpty())
    }
}
