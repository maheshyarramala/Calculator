package com.example.domain.splitbill

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

object PaymentDeepLinkHelper {

  /**
   * Launch native UPI payment application (Google Pay, PhonePe, Paytm, BHIM)
   */
  fun launchUpiPayment(
    context: Context,
    upiId: String,
    payeeName: String,
    amount: Double,
    currency: String = "INR",
    note: String = "OmniCalc Split Bill"
  ) {
    if (upiId.isBlank()) {
      Toast.makeText(context, "No UPI ID provided for $payeeName", Toast.LENGTH_SHORT).show()
      return
    }

    val formattedAmount = String.format(Locale.US, "%.2f", amount)
    val uriString = "upi://pay?pa=${Uri.encode(upiId)}&pn=${Uri.encode(payeeName)}&am=$formattedAmount&cu=$currency&tn=${Uri.encode(note)}"
    val uri = Uri.parse(uriString)

    val intent = Intent(Intent.ACTION_VIEW, uri)
    val chooser = Intent.createChooser(intent, "Pay ₹$formattedAmount with")
    try {
      context.startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(context, "No UPI app found on device", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Open PayPal.me in browser or PayPal app
   */
  fun launchPayPal(context: Context, paypalUsername: String, amount: Double) {
    if (paypalUsername.isBlank()) {
      Toast.makeText(context, "No PayPal username provided", Toast.LENGTH_SHORT).show()
      return
    }
    val cleanUsername = paypalUsername.removePrefix("@").trim()
    val formattedAmount = String.format(Locale.US, "%.2f", amount)
    val url = "https://www.paypal.me/$cleanUsername/$formattedAmount"
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(context, "Cannot open PayPal link", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Share pre-formatted itemized breakdown message via WhatsApp or standard Android Share Sheet
   */
  fun shareBreakdown(
    context: Context,
    billTitle: String,
    currencySymbol: String,
    grandTotal: Double,
    breakdowns: List<PersonBreakdown>,
    transfers: List<DebtTransfer>
  ) {
    val sb = StringBuilder()
    sb.append("🍽️ *OmniCalc Bill Split: $billTitle*\n")
    sb.append("💰 *Total Bill:* $currencySymbol${String.format(Locale.US, "%,.2f", grandTotal)}\n\n")

    sb.append("👥 *Individual Share Breakdown:*\n")
    breakdowns.forEach { b ->
      sb.append("• *${b.participant.name}*: $currencySymbol${String.format(Locale.US, "%,.2f", b.totalToPay)}")
      if (b.paidAmount > 0) {
        sb.append(" (Paid: $currencySymbol${String.format(Locale.US, "%,.2f", b.paidAmount)})")
      }
      if (b.netBalance > 0.005) {
        sb.append(" 🟢 *Gets back $currencySymbol${String.format(Locale.US, "%,.2f", b.netBalance)}*")
      } else if (b.netBalance < -0.005) {
        sb.append(" 🔴 *Owes $currencySymbol${String.format(Locale.US, "%,.2f", Math.abs(b.netBalance))}*")
      }
      sb.append("\n")
    }

    if (transfers.isNotEmpty()) {
      sb.append("\n⚡ *Settlement Transfers (Who Owes Whom):*\n")
      transfers.forEach { t ->
        sb.append("👉 *${t.fromParticipant.name}* ➔ pays *${t.toParticipant.name}* : $currencySymbol${String.format(Locale.US, "%,.2f", t.amount)}")
        if (t.toParticipant.upiId.isNotBlank()) {
          sb.append(" (UPI: `${t.toParticipant.upiId}`)")
        }
        sb.append("\n")
      }
    }

    sb.append("\n✨ Split and settled via OmniCalc")

    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, sb.toString())
      type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Bill Summary"))
  }

  /**
   * Send a gentle one-tap reminder to a debtor
   */
  fun sendNudgeReminder(
    context: Context,
    debtorName: String,
    creditorName: String,
    amount: Double,
    currencySymbol: String,
    billTitle: String,
    creditorUpiId: String
  ) {
    val message = "Hey $debtorName! Just a friendly reminder for $billTitle: your share is $currencySymbol${String.format(Locale.US, "%,.2f", amount)}." +
        if (creditorUpiId.isNotBlank()) " You can pay directly to UPI: $creditorUpiId" else ""

    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, message)
      type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Send Reminder to $debtorName"))
  }
}
