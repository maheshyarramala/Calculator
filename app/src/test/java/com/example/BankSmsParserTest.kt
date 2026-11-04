package com.example

import com.example.data.local.entity.BankAccount
import com.example.domain.finance.BankSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsParserTest {

  @Test
  fun parsesExpenseAndMatchesMerchantCategoryAndAccount() {
    val account = BankAccount(
      id = 7,
      bankName = "HDFC Bank",
      accountType = "Savings",
      accountNumber = "123456789012"
    )

    val result = BankSmsParser.parseSms(
      "Rs. 1,250.50 debited from A/c XX9012 at Swiggy",
      listOf(account)
    )

    assertEquals(1250.50, result?.amount ?: 0.0, 0.001)
    assertFalse(result?.isIncome ?: true)
    assertEquals("Swiggy", result?.merchantOrTitle)
    assertEquals("Food & Dining", result?.suggestedCategory)
    assertEquals(7L, result?.matchedAccountId)
  }

  @Test
  fun recognizesIncomeAndBankName() {
    val account = BankAccount(
      id = 8,
      bankName = "ICICI Bank",
      accountType = "Savings"
    )

    val result = BankSmsParser.parseSms(
      "INR 50,000 credited to your ICICI Bank account from ACME",
      listOf(account)
    )

    assertTrue(result?.isIncome ?: false)
    assertEquals(50000.0, result?.amount ?: 0.0, 0.001)
    assertEquals(8L, result?.matchedAccountId)
  }

  @Test
  fun returnsNullWhenSmsHasNoValidAmount() {
    assertNull(BankSmsParser.parseSms("Your payment was successful", emptyList()))
    assertNull(BankSmsParser.parseSms("Rs. 0 debited", emptyList()))
  }
}
