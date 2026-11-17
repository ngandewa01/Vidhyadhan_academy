package `in`.vidyadhan.academy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.vidyadhan.academy.data.AppDb
import `in`.vidyadhan.academy.lock.LockStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class FullFlowTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun upgradeFromOldDatabase() {
        // a version-1 database exactly like the first test builds left on the phone
        val f = ctx.getDatabasePath("academy.db"); f.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            d.execSQL("CREATE TABLE Student (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, mobile TEXT NOT NULL, aadhaar TEXT NOT NULL, course TEXT NOT NULL, totalFee INTEGER NOT NULL, joinedOn INTEGER NOT NULL, notes TEXT NOT NULL)")
            d.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            d.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'oldhash')")
            d.version = 1
        }
        val db = AppDb.get(ctx)
        runBlocking { db.students().allStudents(); db.library().allMembers() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class UnlockFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun unlockAndOpenEveryTab() {
        LockStore(ApplicationProvider.getApplicationContext()).setPin("1234")
        rule.activityRule.scenario.recreate()
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitForIdle()
        listOf("Students", "Enquiries", "Library", "Settings", "Home").forEach {
            rule.onAllNodesWithText(it).onLast().performClick(); rule.waitForIdle()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AcademyApp::class)
class PickersTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun backupRestoreExportButtonsDoNotCrash() {
        LockStore(ApplicationProvider.getApplicationContext()).setPin("1234")
        rule.activityRule.scenario.recreate()
        listOf("1", "2", "3", "4").forEach { rule.onNodeWithText(it).performClick() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Settings").onLast().performClick()
        rule.waitForIdle(); Thread.sleep(300); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        rule.onNodeWithText("Backup & export").performScrollTo().performClick()
        rule.waitForIdle(); Thread.sleep(300); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        listOf("Back up now", "Restore from a backup", "Export students to Excel").forEach {
            rule.onNodeWithText(it).performScrollTo().performClick(); rule.waitForIdle()
        }
    }
}
