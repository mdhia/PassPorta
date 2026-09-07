package org.shadowgrove.passporta.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.shadowgrove.passporta.data.local.PassDatabase
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.repository.PassRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * End-to-end test of the import: Uri -> parser -> logo file -> Room.
 *
 * Needs a device/emulator, because bitmap decoding, palette and Room actually run here.
 */
@RunWith(AndroidJUnit4::class)
class PassImporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: PassDatabase
    private lateinit var repository: PassRepository
    private lateinit var assetStore: PassAssetStore
    private lateinit var importer: PassImporter
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, PassDatabase::class.java).build()
        repository = PassRepository(database.passDao())
        filesDir = File(context.cacheDir, "importer-test-${System.nanoTime()}").apply { mkdirs() }
        assetStore = PassAssetStore(filesDir)
        importer = PassImporter(context, repository, assetStore)
    }

    @After
    fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    @Test
    fun importsPkPassWithLogoAndColor() = runBlocking {
        val uri = writeArchive("kundenkarte.pkpass", pkPassBytes(withLogo = true))

        val result = importer.importFromUri(uri)

        assertTrue("Import failed: $result", result is PassImportResult.Success)
        val success = result as PassImportResult.Success
        assertEquals(1, success.importedCount)
        assertEquals(0, success.updatedCount)
        assertEquals(true, success.integrityVerified)

        val pass = requireNotNull(repository.getPass(success.passIds.first()))
        assertEquals("Kundenkarten", pass.folderName)
        assertEquals("Beispiel Club", pass.title)
        assertEquals("Erika Mustermann", pass.ownerName)
        assertEquals(BarcodeType.PDF417, pass.barcodeType)
        assertEquals(0xFF5A3CC8.toInt(), pass.backgroundColor)

        val logo = assetStore.resolve(pass.logoPath)
        assertNotNull("Logo was not saved", logo)
        assertTrue(logo!!.length() > 0)
    }

    @Test
    fun updatesExistingPassInsteadOfCreatingADuplicate() = runBlocking {
        val first = importer.importFromUri(writeArchive("a.pkpass", pkPassBytes(withLogo = false)))
        val second = importer.importFromUri(writeArchive("b.pkpass", pkPassBytes(withLogo = false)))

        val firstId = (first as PassImportResult.Success).passIds.single()
        val secondResult = second as PassImportResult.Success

        assertEquals(firstId, secondResult.passIds.single())
        assertEquals(1, secondResult.updatedCount)
        assertEquals(1, repository.count())
    }

    @Test
    fun derivesBackgroundColorFromTheLogo() = runBlocking {
        val uri = writeArchive(
            "ohne-farbe.pkpass",
            pkPassBytes(withLogo = true, backgroundColor = null),
        )

        val result = importer.importFromUri(uri) as PassImportResult.Success
        val pass = requireNotNull(repository.getPass(result.passIds.first()))

        // The test logo is solid orange - the palette must find this tone.
        assertEquals(0xFF, Color.alpha(pass.backgroundColor))
        assertTrue(
            "Unexpected color: ${Integer.toHexString(pass.backgroundColor)}",
            Color.red(pass.backgroundColor) > Color.blue(pass.backgroundColor),
        )
    }

    @Test
    fun reportsInvalidArchives() = runBlocking {
        val uri = writeArchive("kaputt.pkpass", "kein zip".toByteArray())

        val result = importer.importFromUri(uri)

        assertEquals(
            ImportFailure.INVALID_PKPASS,
            (result as PassImportResult.Failure).failure,
        )
    }

    @Test
    fun importsWalletLinkWithoutNetwork() = runBlocking {
        val result = importer.importFromLink("https://pay.google.com/gp/v/save/${walletJwt()}")

        assertTrue("Import failed: $result", result is PassImportResult.Success)
        val pass = requireNotNull(
            repository.getPass((result as PassImportResult.Success).passIds.first()),
        )
        assertEquals("Kundenkarten", pass.folderName)
        assertEquals("Beispiel Bonus", pass.title)
        assertEquals("LOYALTY-BARCODE", pass.barcodeData)
        assertEquals(BarcodeType.QR, pass.barcodeType)
        assertEquals(0xFF1A73E8.toInt(), pass.backgroundColor)
        assertNull(pass.logoPath)
    }

    /** Builds an unsigned wallet token - the signature is deliberately not verified. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun walletJwt(): String {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val payload = """
            {
              "typ": "savetowallet",
              "payload": {
                "loyaltyClasses": [
                  {
                    "id": "issuer/class/1",
                    "issuerName": "Beispiel GmbH",
                    "programName": "Beispiel Bonus",
                    "hexBackgroundColor": "#1a73e8"
                  }
                ],
                "loyaltyObjects": [
                  {
                    "id": "issuer/object/1",
                    "classId": "issuer/class/1",
                    "accountName": "Erika Mustermann",
                    "accountId": "1234567890",
                    "barcode": { "type": "QR_CODE", "value": "LOYALTY-BARCODE" }
                  }
                ]
              }
            }
        """.trimIndent()
        val header = encoder.encode("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        return "$header.${encoder.encode(payload.toByteArray())}.SIGNATURPLATZHALTER"
    }

    private fun writeArchive(name: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, name).apply { writeBytes(bytes) }
        return Uri.fromFile(file)
    }

    /** Creates a complete `.pkpass` including a valid `manifest.json`. */
    private fun pkPassBytes(withLogo: Boolean, backgroundColor: String? = "rgb(90, 60, 200)"): ByteArray {
        val passJson = """
            {
              "formatVersion": 1,
              "serialNumber": "SN-1",
              "organizationName": "Beispiel GmbH",
              "description": "Kundenkarte",
              "logoText": "Beispiel Club",
              ${backgroundColor?.let { """"backgroundColor": "$it",""" } ?: ""}
              "barcodes": [
                { "format": "PKBarcodeFormatPDF417", "message": "9012345678" }
              ],
              "storeCard": {
                "primaryFields": [
                  { "key": "memberName", "label": "Inhaber", "value": "Erika Mustermann" }
                ]
              }
            }
        """.trimIndent()

        val files = buildMap {
            put("pass.json", passJson.toByteArray())
            if (withLogo) put("logo.png", orangeLogoPng())
        }
        val manifest = files.entries.joinToString(prefix = "{", postfix = "}") { (name, bytes) ->
            """"$name":"${sha1Hex(bytes)}""""
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (files + ("manifest.json" to manifest.toByteArray())).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun orangeLogoPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFE65100.toInt())
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }

    private fun sha1Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}



