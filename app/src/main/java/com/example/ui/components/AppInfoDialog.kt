package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppInfoType {
  ABOUT,
  PRIVACY,
  TERMS,
  CONTACT,
  DONATE
}

@Composable
fun AppInfoModalDialog(
  type: AppInfoType,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
    title = {
      Text(
        when (type) {
          AppInfoType.ABOUT -> "About OmniCalc"
          AppInfoType.PRIVACY -> "Privacy Policy"
          AppInfoType.TERMS -> "Terms & Conditions"
          AppInfoType.CONTACT -> "Contact Us & Feedback"
          AppInfoType.DONATE -> "Support Developer"
        },
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        when (type) {
          AppInfoType.ABOUT -> {
            Text("OmniCalc is an all-in-one financial, loan tracking, and utility calculation suite.")
            Divider()
            Text("Credits & Developer:", fontWeight = FontWeight.SemiBold)
            Text("Developed by Mahesh Yarramala")
            Button(onClick = {
              launchExternalIntent(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/mahesh.yarramala")),
                "No app is available to open this link."
              )
            }) {
              Text("Instagram: @mahesh.yarramala")
            }
          }

          AppInfoType.PRIVACY -> {
            Text(
              "Privacy Policy:\n\n" +
                "1. Data Collection: OmniCalc operates primarily offline. All calculations, loan details, and financial logs are stored locally on your device.\n\n" +
                "2. Notifications: Notification permissions are used for scheduled EMI reminders and alert triggers.\n\n" +
                "3. Third-party Sharing: Personal user data is not sold, rented, or shared with third-party advertisers."
            )
          }

          AppInfoType.TERMS -> {
            Text(
              "Terms & Conditions:\n\n" +
                "1. All calculations provided by OmniCalc are for informational and planning purposes only.\n\n" +
                "2. Financial figures, interest calculations, and bank EMI deductions should be verified with your respective banking institution."
            )
          }

          AppInfoType.CONTACT -> {
            Text("We value your suggestions and feedback!")
            Text("Email:", fontWeight = FontWeight.SemiBold)
            Text("devops.maheshbabu@gmail.com")
            Button(onClick = {
              val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:devops.maheshbabu@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "OmniCalc Feedback / Support")
              }
              launchExternalIntent(context, emailIntent, "No email app is available.")
            }) {
              Text("Send Feedback Email")
            }
          }

          AppInfoType.DONATE -> {
            Text("If OmniCalc helped you organize your finances, consider supporting ongoing development!")
            Text("UPI ID: maheshyarramala@ybl", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = {
                clipboardManager.setText(AnnotatedString("maheshyarramala@ybl"))
                Toast.makeText(context, "UPI ID copied.", Toast.LENGTH_SHORT).show()
              }) {
                Text("Copy UPI ID")
              }
              Button(onClick = {
                val upiIntent = Intent(
                  Intent.ACTION_VIEW,
                  Uri.parse("upi://pay?pa=maheshyarramala@ybl&pn=Mahesh%20Yarramala&cu=INR")
                )
                launchExternalIntent(
                  context,
                  Intent.createChooser(upiIntent, "Pay with UPI"),
                  "No UPI app is available."
                )
              }) {
                Text("Open UPI App")
              }
            }
          }
        }
      }
    }
  )
}

private fun launchExternalIntent(
  context: android.content.Context,
  intent: Intent,
  unavailableMessage: String
) {
  try {
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
  }
}
