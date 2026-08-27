package com.giftcondoctor.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.ui.screens.ExpiryDateField
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpiryDateFieldInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensCalendarAndConfirmsTheInitialIsoDate() {
        var selectedValue = ""
        var selectionCount = 0

        composeRule.setContent {
            var value by remember { mutableStateOf("2026-08-20") }
            var callbackCount by remember { mutableIntStateOf(0) }
            GDTheme {
                ExpiryDateField(
                    value = value,
                    onValueChange = {
                        value = it
                        selectedValue = it
                        callbackCount += 1
                        selectionCount = callbackCount
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("만료일 달력 열기").performClick()
        composeRule.onNodeWithText("선택").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("2026-08-20", selectedValue)
            assertEquals(1, selectionCount)
        }
    }

    @Test
    fun invalidDateShowsInlineError() {
        composeRule.setContent {
            GDTheme {
                ExpiryDateField(
                    value = "2026-02-30",
                    onValueChange = {},
                    errorText = "YYYY-MM-DD 형식의 올바른 날짜를 입력해 주세요."
                )
            }
        }

        composeRule.onNodeWithText("YYYY-MM-DD 형식의 올바른 날짜를 입력해 주세요.")
            .assertIsDisplayed()
    }
}
