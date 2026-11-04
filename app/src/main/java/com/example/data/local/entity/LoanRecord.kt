package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.pow

@Entity(tableName = "loans")
data class LoanRecord(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val loanName: String,
  val loanCategory: String = "PERSONAL", // HOME, PERSONAL, CAR, MOBILE, EDUCATION, OTHER
  val principalAmount: Double,
  val annualInterestRate: Double,
  val tenureMonths: Int,
  val emiAmount: Double,
  val startDate: String,
  val paidEmisCount: Int = 0,
  val notes: String = "",
  val bankAccountId: Long? = null,
  val bankAccountName: String = ""
) {
  val remainingEmisCount: Int
    get() = maxOf(0, tenureMonths - paidEmisCount)

  val totalRepayment: Double
    get() = emiAmount * tenureMonths

  val totalInterestPayable: Double
    get() = maxOf(0.0, totalRepayment - principalAmount)

  val totalPaidAmount: Double
    get() = emiAmount * minOf(paidEmisCount, tenureMonths)

  val remainingAmount: Double
    get() = emiAmount * remainingEmisCount

  val progressFraction: Float
    get() = if (tenureMonths > 0) (paidEmisCount.toFloat() / tenureMonths).coerceIn(0f, 1f) else 0f

  val estimatedRemainingPrincipal: Double
    get() = maxOf(0.0, principalAmount * (1f - progressFraction))

  val estimatedPrincipalPaid: Double
    get() = principalAmount - estimatedRemainingPrincipal

  val isFullyPaid: Boolean
    get() = paidEmisCount >= tenureMonths

  companion object {
    fun calculateEmi(principal: Double, annualRate: Double, months: Int): Double {
      if (principal <= 0 || months <= 0) return 0.0
      if (annualRate <= 0) return principal / months
      val monthlyRate = (annualRate / 12.0) / 100.0
      val factor = (1.0 + monthlyRate).pow(months.toDouble())
      return (principal * monthlyRate * factor) / (factor - 1.0)
    }
  }
}
