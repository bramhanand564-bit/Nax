package com.jarvis.os.data

object PrivacyShield {

    private val defaultBlockedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp",
        "com.sbi.lotusintouch",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
        "com.whatsapp.w4b.payments",
        "com.bitwarden.vault",
        "com.x8bit.bitwarden",
        "com.onepassword.android",
        "com.google.android.apps.authenticator2"
    )

    private val sensitiveKeywords = listOf(
        "upi", "bank", "paisa", "paytm", "phonepe",
        "password", "authenticator", "wallet", "creditcard", "netbanking"
    )

    fun isProtectedApp(packageName: String?, customBlocked: Set<String> = emptySet()): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        if (pkg in defaultBlockedPackages) return true
        if (customBlocked.any { it.lowercase() == pkg }) return true
        return sensitiveKeywords.any { pkg.contains(it) }
    }

    fun isSensitiveText(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("upi pin", "enter mpin", "cvv", "otp is", "one time password", "netbanking password")
            .any { lower.contains(it) }
    }

    fun getDefaultProtectedLabels(): List<String> = listOf(
        "GPay / PhonePe / Paytm / BHIM",
        "All Banking Apps (SBI, HDFC, ICICI, Axis, Kotak)",
        "Password Managers & 2FA Authenticators",
        "UPI PIN / OTP / CVV Screens (Auto-Masked)"
    )
}
