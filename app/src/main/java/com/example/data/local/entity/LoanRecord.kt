package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
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
  val bankAccountName: String = "",
  @ColumnInfo(defaultValue = "1")
  val dueDayOfMonth: Int = 1
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

  val cumulativePrincipalPaid: Double
    get() = calculateCumulativePrincipal(principalAmount, annualInterestRate, tenureMonths, paidEmisCount, emiAmount)

  val remainingPrincipal: Double
    get() = maxOf(0.0, principalAmount - cumulativePrincipalPaid)

  // Backward-compatible aliases pointing to exact financial amounts
  val estimatedRemainingPrincipal: Double
    get() = remainingPrincipal

  val estimatedPrincipalPaid: Double
    get() = cumulativePrincipalPaid

  val isFullyPaid: Boolean
    get() = paidEmisCount >= tenureMonths

  val activeMonthlyOutflow: Double
    get() = if (isFullyPaid) 0.0 else emiAmount

  companion object {
    fun calculateEmi(principal: Double, annualRate: Double, months: Int): Double {
      if (principal <= 0 || months <= 0) return 0.0
      if (annualRate <= 0) return principal / months
      val monthlyRate = (annualRate / 12.0) / 100.0
      val factor = (1.0 + monthlyRate).pow(months.toDouble())
      return (principal * monthlyRate * factor) / (factor - 1.0)
    }

    /**
     * Computes the exact cumulative principal component paid across [paidMonths]
     * using standard financial reducing-balance amortization.
     */
    fun calculateCumulativePrincipal(
      principal: Double,
      annualRate: Double,
      tenureMonths: Int,
      paidMonths: Int,
      emi: Double
    ): Double {
      if (principal <= 0.0 || paidMonths <= 0) return 0.0
      if (paidMonths >= tenureMonths) return principal
      if (annualRate <= 0.0) return minOf(principal, (principal / tenureMonths) * paidMonths)

      val monthlyRate = (annualRate / 12.0) / 100.0
      var currentBalance = principal
      var totalPrincipal = 0.0
      val monthsToIterate = minOf(paidMonths, tenureMonths)

      for (m in 1..monthsToIterate) {
        val interestPart = currentBalance * monthlyRate
        val principalPart = minOf(currentBalance, maxOf(0.0, emi - interestPart))
        totalPrincipal += principalPart
        currentBalance = maxOf(0.0, currentBalance - principalPart)
      }
      return minOf(principal, totalPrincipal)
    }
  }
}
