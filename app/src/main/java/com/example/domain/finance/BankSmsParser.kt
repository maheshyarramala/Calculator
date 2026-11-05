package com.example.domain.finance

import com.example.data.local.entity.BankAccount
import java.util.regex.Pattern

data class ParsedSmsTransaction(
  val amount: Double,
  val isIncome: Boolean,
  val merchantOrTitle: String,
  val suggestedCategory: String,
  val matchedAccountId: Long?,
  val matchedAccountName: String,
  val rawNotes: String
)

object BankSmsParser {

  private val amountPattern = Pattern.compile(
    """(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
    Pattern.CASE_INSENSITIVE
  )

  private val expenseMerchantPattern = Pattern.compile(
    """(?:to|at|towards|info:?|vpa)\s+(?!a/c|acct|account|bank)([A-Za-z0-9._@-]+(?:\s+[A-Za-z0-9._-]+)?)""",
    Pattern.CASE_INSENSITIVE
  )

  private val incomeSenderPattern = Pattern.compile(
    """(?:from)\s+(?!a/c|acct|account|bank)([A-Za-z0-9._@-]+(?:\s+[A-Za-z0-9._-]+)?)""",
    Pattern.CASE_INSENSITIVE
  )

  private val fallbackMerchantPattern = Pattern.compile(
    """(?:to|at|towards|from|info:?|vpa)\s+(?!a/c|acct|account|bank)([A-Za-z0-9._@-]+(?:\s+[A-Za-z0-9._-]+)?)""",
    Pattern.CASE_INSENSITIVE
  )

  private val accountLast4Pattern = Pattern.compile(
    """(?:a/c|acct|card|xx+|ending)\s*(?:no\.?\s*)?[xX*]*([0-9]{3,4})""",
    Pattern.CASE_INSENSITIVE
  )

  fun parseSms(smsText: String, accounts: List<BankAccount>): ParsedSmsTransaction? {
    val clean = smsText.trim()
    if (clean.isEmpty()) return null

    val amountMatcher = amountPattern.matcher(clean)
    if (!amountMatcher.find()) return null
    val amount = amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0.0) return null

    val lower = clean.lowercase()
    val isIncome = (lower.contains("credited") || lower.contains("received") ||
      lower.contains("deposited") || lower.contains("refund")) &&
      !lower.contains("debited")

    var merchant = "UPI / Bank Txn"
    val merchantMatcher = if (isIncome) {
      val incMatcher = incomeSenderPattern.matcher(clean)
      if (incMatcher.find()) incMatcher else fallbackMerchantPattern.matcher(clean)
    } else {
      val expMatcher = expenseMerchantPattern.matcher(clean)
      if (expMatcher.find()) expMatcher else fallbackMerchantPattern.matcher(clean)
    }

    if (merchantMatcher.find() || merchantMatcher.reset().find()) {
      val rawMerchant = merchantMatcher.group(1)?.trim().orEmpty()
      merchant = rawMerchant
        .substringBefore("@")
        .replace(Regex("[^A-Za-z0-9 ]"), " ")
        .trim()
        .replaceFirstChar { it.uppercase() }
        .ifBlank { "UPI Payment" }
    }

    val category = when {
      lower.contains("swiggy") || lower.contains("zomato") ||
        lower.contains("restaurant") || lower.contains("cafe") ||
        lower.contains("biryani") -> "Food & Dining"
      lower.contains("amazon") || lower.contains("flipkart") ||
        lower.contains("myntra") || lower.contains("mart") -> "Shopping"
      lower.contains("irctc") || lower.contains("uber") ||
        lower.contains("ola") || lower.contains("rapido") ||
        lower.contains("petrol") || lower.contains("fuel") -> "Fuel & Transport"
      lower.contains("netflix") || lower.contains("bookmyshow") ||
        lower.contains("prime") || lower.contains("spotify") ||
        lower.contains("movie") -> "Entertainment"
      lower.contains("zerodha") || lower.contains("dhan") ||
        lower.contains("angel") || lower.contains("groww") ||
        lower.contains("mutual fund") -> "Investments"
      lower.contains("apollo") || lower.contains("pharma") ||
        lower.contains("hospital") || lower.contains("med") -> "Health & Medical"
      else -> "Bills & Utilities"
    }

    var matchedAccount: BankAccount? = null
    val accountMatcher = accountLast4Pattern.matcher(clean)
    if (accountMatcher.find()) {
      val lastDigits = accountMatcher.group(1).orEmpty()
      if (lastDigits.isNotEmpty()) {
        matchedAccount = accounts.find { it.accountNumber.endsWith(lastDigits) }
      }
    }
    if (matchedAccount == null) {
      matchedAccount = accounts.find { lower.contains(it.bankName.lowercase()) }
        ?: accounts.firstOrNull()
    }

    return ParsedSmsTransaction(
      amount = amount,
      isIncome = isIncome,
      merchantOrTitle = merchant,
      suggestedCategory = category,
      matchedAccountId = matchedAccount?.id,
      matchedAccountName = matchedAccount?.bankName.orEmpty(),
      rawNotes = "Auto-parsed from SMS"
    )
  }
}
