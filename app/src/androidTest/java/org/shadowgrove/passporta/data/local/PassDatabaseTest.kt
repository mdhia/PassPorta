package org.shadowgrove.passporta.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.shadowgrove.passporta.data.local.dao.PassDao
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.util.PassColors

@RunWith(AndroidJUnit4::class)
class PassDatabaseTest {

    private lateinit var database: PassDatabase
    private lateinit var dao: PassDao
    private lateinit var repository: PassRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PassDatabase::class.java,
        ).build()
        dao = database.passDao()
        repository = PassRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savesAndReadsPass() = runBlocking {
        val id = repository.save(samplePass(title = "  Bahncard 50  ", folder = "Kundenkarten"))

        val loaded = requireNotNull(repository.getPass(id))
        assertEquals("Bahncard 50", loaded.title)
        assertEquals("Kundenkarten", loaded.folderName)
        assertEquals(BarcodeType.AZTEC, loaded.barcodeType)
        assertEquals(0xFF, loaded.backgroundColor ushr 24)
    }

    @Test
    fun groupsFolders() = runBlocking {
        repository.save(samplePass(title = "Flug LH400", folder = "Fluege"))
        repository.save(samplePass(title = "Flug LH401", folder = "Fluege"))
        repository.save(samplePass(title = "Payback", folder = "Kundenkarten"))

        val folders = repository.observeFolders().first()
        assertEquals(2, folders.size)
        assertEquals("Fluege", folders.first().folderName)
        assertEquals(2, folders.first().passCount)
    }

    @Test
    fun findsDuplicatesByBarcode() = runBlocking {
        repository.save(samplePass(barcodeData = "ABC-123"))

        assertNotNull(repository.findDuplicate("ABC-123"))
        assertEquals(null, repository.findDuplicate("XYZ-999"))
    }

    @Test
    fun contrastLogicChoosesReadableText() {
        assertEquals(PassColors.ON_LIGHT, PassColors.contentColorFor(0xFFFFEB3B.toInt()))
        assertEquals(PassColors.ON_DARK, PassColors.contentColorFor(0xFF1A237E.toInt()))
        assertTrue(PassColors.isAccessible(PassColors.ON_DARK, 0xFF1A237E.toInt()))
    }

    private fun samplePass(
        title: String = "Testpass",
        folder: String = PassEntity.DEFAULT_FOLDER,
        barcodeData: String = "1234567890",
    ) = PassEntity(
        folderName = folder,
        title = title,
        subtitle = null,
        ownerName = "Max Mustermann",
        identifier = "4711",
        barcodeData = barcodeData,
        barcodeType = BarcodeType.AZTEC,
        backgroundColor = 0x0037474F,
    )
}
