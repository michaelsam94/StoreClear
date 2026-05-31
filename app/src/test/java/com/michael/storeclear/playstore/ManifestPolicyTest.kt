package com.michael.storeclear.playstore

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPolicyTest {

  @Test
  fun `manifest does not request broad package visibility`() {
    val manifest = File("src/main/AndroidManifest.xml")
    val xml = manifest.readText()

    assertFalse(
      "QUERY_ALL_PACKAGES is not allowed for StoreClear's Play submission",
      xml.contains("android.permission.QUERY_ALL_PACKAGES")
    )
  }

  @Test
  fun `manifest declares targeted launcher package visibility`() {
    val manifest = File("src/main/AndroidManifest.xml")
    val document = DocumentBuilderFactory.newInstance()
      .newDocumentBuilder()
      .parse(manifest)

    val queries = document.getElementsByTagName("queries")
    assertEquals(1, queries.length)

    val actions = document.getElementsByTagName("action")
    val categories = document.getElementsByTagName("category")

    val hasMainAction = (0 until actions.length).any { index ->
      actions.item(index).attributes
        .getNamedItem("android:name")
        ?.nodeValue == "android.intent.action.MAIN"
    }
    val hasLauncherCategory = (0 until categories.length).any { index ->
      categories.item(index).attributes
        .getNamedItem("android:name")
        ?.nodeValue == "android.intent.category.LAUNCHER"
    }

    assertTrue(hasMainAction)
    assertTrue(hasLauncherCategory)
  }
}
