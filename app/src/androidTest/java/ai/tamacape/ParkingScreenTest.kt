package ai.tamacape

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingScreenTest {
    @Test fun aiPagesDiscloseDisconnectedStateAndRestrictionRemovesControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            repeat(30) {
                scenario.onActivity { activity ->
                    ready = activity.window.decorView.descendants().filterIsInstance<Button>().any { it.text == "모험" }
                }
                if (!ready) Thread.sleep(100)
            }
            assertTrue(ready)
            for (kind in listOf("coach", "song")) {
                scenario.onActivity { activity ->
                    activity.onRestrictionChanged(ParkingAccess.PARKED)
                    val open = MainActivity::class.java.getDeclaredMethod("openFamilyAi",
                        String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    open.isAccessible = true
                    open.invoke(activity, kind, 0, 0)
                    val views = activity.window.decorView.descendants().toList()
                    assertTrue(views.filterIsInstance<TextView>().any { it.text == "AI 연결 전" })
                    assertTrue(views.filterIsInstance<Button>().any { it.text == "부모님 AI 연결 설정" })
                    activity.onRestrictionChanged(ParkingAccess.RESTRICTED)
                    assertFalse(activity.window.decorView.descendants().any { it is Button })
                }
            }
        }
    }

    @Test fun restrictedStateReplacesGameplayAndParkingRestoresIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            repeat(30) {
                scenario.onActivity { activity ->
                    ready = activity.window.decorView.descendants().filterIsInstance<Button>()
                        .any { it.text == "모험" }
                }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Parked home should be visible", ready)
            scenario.onActivity { activity ->
                activity.onRestrictionChanged(ParkingAccess.RESTRICTED)
                val blocked = activity.window.decorView
                assertFalse(blocked.descendants().any { it is Button })
                assertTrue(blocked.descendants().filterIsInstance<TextView>()
                    .any { it.text.contains("주행 제한으로 게임이 일시정지") })
                activity.onRestrictionChanged(ParkingAccess.PARKED)
                assertTrue(activity.window.decorView.descendants().filterIsInstance<Button>()
                    .any { it.text == "모험" })
            }
        }
    }

    @Test fun systemBackReturnsThroughPagesWithoutClosingApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            repeat(30) {
                scenario.onActivity { activity ->
                    ready = activity.window.decorView.descendants().filterIsInstance<Button>().any { it.text == "설정" }
                }
                if (!ready) Thread.sleep(100)
            }
            assertTrue(ready)
            fun tap(text: String) { scenario.onActivity { activity ->
                activity.window.decorView.descendants().filterIsInstance<Button>().first { it.text == text }.performClick()
            } }
            fun back() {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            fun expect(text: String) { scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.descendants().filterIsInstance<TextView>().any { it.text == text })
            } }
            tap("설정")
            tap("차량 데이터")
            back()
            expect("우리 가족의 놀이 설정")
            back()
            expect("우리 가족의 친구")
            tap("친구들")
            back()
            expect("우리 가족의 친구")
        }
    }

    @Test fun largeTextScreensKeepNavigationAndRenderWithoutClippingButtons() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(30) { Thread.sleep(100) }
            val settingsField = MainActivity::class.java.getDeclaredField("settings").apply { isAccessible = true }
            var original: GameSettings? = null
            val navigate = MainActivity::class.java.getDeclaredMethod("navigate", String::class.java).apply { isAccessible = true }
            scenario.onActivity {
                original = settingsField.get(it) as GameSettings
                settingsField.set(it, original!!.copy(largeText = true))
                it.onRestrictionChanged(ParkingAccess.PARKED)
            }
            try {
                for (page in listOf("홈", "모험", "친구들", "도감", "설정", "차량", "동요 앨범", "놀이 안내")) {
                    scenario.onActivity { navigate.invoke(it, page) }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val buttons = activity.window.decorView.descendants().filterIsInstance<Button>().toList()
                        for (tab in listOf("홈", "모험", "친구들", "도감", "설정")) {
                            val button = buttons.first { it.text == tab }
                            val bounds = android.graphics.Rect()
                            assertTrue("$page: $tab visible", button.getGlobalVisibleRect(bounds))
                            assertTrue("$page: $tab height", bounds.height() >= button.height - 2)
                        }
                        for (button in buttons.filter { it.isShown && it.height > 0 }) {
                            val layout = button.layout ?: continue
                            assertTrue("$page: text clipped: ${button.text}",
                                layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom + 2)
                        }
                    }
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
                    java.io.File(directory, "$page.png").outputStream().use {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
            } finally { scenario.onActivity { settingsField.set(it, original); navigate.invoke(it, "홈") } }
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
        }
    }
}
