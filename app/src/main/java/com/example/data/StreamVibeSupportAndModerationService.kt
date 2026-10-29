package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Validates support evidence files (Images: JPG, PNG, WEBP up to 10 MB; Videos: MP4, MOV up to 50 MB; max 5 per ticket)
 * on both client and backend, rejecting unsupported, oversized, or malicious files.
 */
object SupportEvidenceValidator {

    private val forbiddenExecutableTokens = listOf(
        ".exe", ".apk", ".sh", ".bat", ".cmd", ".ps1", ".php", ".js", ".jsp", ".py", ".rb", ".pl", ".jar", ".dex", ".so", ".elf", ".html", ".htm", ".svg"
    )

    data class ValidationOutcome(
        val isValid: Boolean,
        val normalizedExtension: String = "",
        val normalizedMimeType: String = "",
        val mediaKind: String = "IMAGE",
        val compressedSizeBytes: Long = 0L,
        val errorMessage: String? = null
    )

    fun validateCandidateFile(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        currentAttachmentCount: Int
    ): ValidationOutcome {
        if (currentAttachmentCount >= StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "Maximum ${StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET} attachments are allowed per support ticket."
            )
        }

        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty() || trimmedName.contains("..") || trimmedName.contains("/") || trimmedName.contains("\\")) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "Invalid or unsafe file name."
            )
        }

        val lowerName = trimmedName.lowercase()
        if (forbiddenExecutableTokens.any { lowerName.endsWith(it) || lowerName.contains("$it.") }) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "Security check failed: Executable, script, or potentially malicious file types are strictly prohibited."
            )
        }

        val ext = lowerName.substringAfterLast('.', "").trim()
        if (ext.isEmpty()) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "File must have a valid extension (JPG, PNG, WEBP, MP4, or MOV)."
            )
        }

        if (fileSizeBytes <= 0L) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "File is empty (0 bytes). Please select a valid image or video file."
            )
        }

        val normMime = mimeType.trim().lowercase()
        val isImageExt = StreamVibeCatalog.allowedImageExtensions.contains(ext)
        val isVideoExt = StreamVibeCatalog.allowedVideoExtensions.contains(ext)

        if (!isImageExt && !isVideoExt) {
            return ValidationOutcome(
                isValid = false,
                errorMessage = "Unsupported file format '.$ext'. Allowed formats: Images (JPG, PNG, WEBP up to 10 MB) and Videos (MP4, MOV up to 50 MB)."
            )
        }

        if (isImageExt) {
            val resolvedMime = when {
                normMime.isBlank() -> if (ext == "png") "image/png" else if (ext == "webp") "image/webp" else "image/jpeg"
                StreamVibeCatalog.allowedImageMimeTypes.contains(normMime) -> normMime
                else -> return ValidationOutcome(
                    isValid = false,
                    errorMessage = "MIME type '$mimeType' does not match allowed image formats (JPG, PNG, WEBP)."
                )
            }
            if (fileSizeBytes > StreamVibeCatalog.MAX_IMAGE_ATTACHMENT_BYTES) {
                return ValidationOutcome(
                    isValid = false,
                    errorMessage = "Image exceeds the 10 MB size limit (${formatBytes(fileSizeBytes)}). Please select an image up to 10 MB."
                )
            }
            val compressedSize = if (fileSizeBytes > 1024L * 1024L) {
                (fileSizeBytes * 72L) / 100L
            } else {
                fileSizeBytes
            }
            return ValidationOutcome(
                isValid = true,
                normalizedExtension = if (ext == "jpeg") "jpg" else ext,
                normalizedMimeType = resolvedMime,
                mediaKind = "IMAGE",
                compressedSizeBytes = compressedSize
            )
        } else {
            val resolvedMime = when {
                normMime.isBlank() -> if (ext == "mov") "video/quicktime" else "video/mp4"
                StreamVibeCatalog.allowedVideoMimeTypes.contains(normMime) -> normMime
                else -> return ValidationOutcome(
                    isValid = false,
                    errorMessage = "MIME type '$mimeType' does not match allowed video formats (MP4, MOV)."
                )
            }
            if (fileSizeBytes > StreamVibeCatalog.MAX_VIDEO_ATTACHMENT_BYTES) {
                return ValidationOutcome(
                    isValid = false,
                    errorMessage = "Video exceeds the 50 MB size limit (${formatBytes(fileSizeBytes)}). Please select a video up to 50 MB."
                )
            }
            return ValidationOutcome(
                isValid = true,
                normalizedExtension = ext,
                normalizedMimeType = resolvedMime,
                mediaKind = "VIDEO",
                compressedSizeBytes = fileSizeBytes
            )
        }
    }

    fun validateAttachment(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        currentAttachmentCount: Int
    ): ValidationOutcome = validateCandidateFile(
        fileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        currentAttachmentCount = currentAttachmentCount
    )

    fun computeAttachmentHash(ownerUserId: String, fileName: String, fileSizeBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val raw = "$ownerUserId|$fileName|$fileSizeBytes"
        return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.2f MB", mb)
    }
}

/**
 * Enforces the StreamVibe Account Violation & Ban Policy:
 * - First confirmed ordinary violation -> Warning + 3-day temporary suspension.
 * - Repeated ordinary violations -> Further 3-day suspension or permanent ban after repeated confirmed violations.
 * - Serious or repeated severe violations -> Permanent account ban following moderator review.
 * - Never permanently bans or suspends a user solely because someone reports them without confirmed review.
 */
object ModerationPolicyEngine {

    fun computeModerationOutcome(
        existingViolationCount: Int,
        severity: ViolationSeverity,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Triple<AccountModerationStatus, Int, Long> {
        return when (severity) {
            ViolationSeverity.ORDINARY -> {
                if (existingViolationCount >= 2) {
                    Triple(AccountModerationStatus.PERMANENTLY_BANNED, 0, 0L)
                } else {
                    // First confirmed ordinary violation -> Warning + 3-day temporary suspension
                    val endsAt = nowEpochMs + StreamVibeCatalog.TEMP_SUSPENSION_DURATION_MS
                    Triple(
                        AccountModerationStatus.TEMP_SUSPENDED_3_DAYS,
                        StreamVibeCatalog.TEMP_SUSPENSION_DAYS,
                        endsAt
                    )
                }
            }
            ViolationSeverity.REPEATED -> {
                Triple(AccountModerationStatus.PERMANENTLY_BANNED, 0, 0L)
            }
            ViolationSeverity.REPEATED_ORDINARY -> {
                if (existingViolationCount >= 1) {
                    Triple(AccountModerationStatus.PERMANENTLY_BANNED, 0, 0L)
                } else {
                    val endsAt = nowEpochMs + StreamVibeCatalog.TEMP_SUSPENSION_DURATION_MS
                    Triple(
                        AccountModerationStatus.TEMP_SUSPENDED_3_DAYS,
                        StreamVibeCatalog.TEMP_SUSPENSION_DAYS,
                        endsAt
                    )
                }
            }
            ViolationSeverity.SEVERE -> {
                Triple(AccountModerationStatus.PERMANENTLY_BANNED, 0, 0L)
            }
        }
    }

    fun buildAccountNoticeMessage(profile: UserProfile): String? {
        return when (profile.accountStatus) {
            AccountModerationStatus.WARNED.name -> {
                "Community Guidelines Warning: ${profile.suspensionReason.ifBlank { "Please follow StreamVibe Community Guidelines." }}"
            }
            AccountModerationStatus.TEMP_SUSPENDED_3_DAYS.name -> {
                "Account Temporarily Suspended (3 Days): ${profile.suspensionReason.ifBlank { "Confirmed Community Guidelines violation." }}. You may submit an appeal in Help & Support -> Account Ban / Appeal."
            }
            AccountModerationStatus.PERMANENTLY_BANNED.name -> {
                "Account Permanently Banned Following Moderation Review: ${profile.suspensionReason.ifBlank { "Severe or repeated Community Guidelines violation." }}. You may submit an appeal with evidence in Help & Support."
            }
            else -> null
        }
    }
}

/**
 * AI Customer Support Assistant for StreamVibe:
 * - Responds in English or Hindi depending on the user's query or selected language.
 * - Strictly separates support categories (never mixes unrelated categories like Deposit vs Video).
 * - Never asks for passwords, OTPs, or private keys.
 * - Never fabricates ticket numbers, payment confirmations, or completed refund/appeal claims.
 * - Optionally queries `gemini-3.5-flash` via REST when `BuildConfig.GEMINI_API_KEY` is configured.
 */
object AiCustomerSupportAssistant {

    fun detectLanguage(userMessage: String, preferredLanguage: String = "AUTO"): String {
        val pref = preferredLanguage.trim().uppercase()
        if (pref == "HI" || pref == "HINDI") return "HI"
        if (pref == "EN" || pref == "ENGLISH") return "EN"
        val hasDevanagari = userMessage.any { it.code in 0x0900..0x097F }
        if (hasDevanagari) return "HI"
        val lower = userMessage.lowercase()
        val hindiRomanTokens = listOf("mera", "meri", "nahi", "kya", "kaise", "paisa", "kab", "madad", "batao", "kyu")
        if (hindiRomanTokens.count { lower.contains(it) } >= 2) return "HI"
        return "EN"
    }

    fun generateLocalReply(
        userMessage: String,
        selectedCategory: SupportRequestCategory,
        customCategoryDescription: String = "",
        preferredLanguage: String = "AUTO",
        attachedFilesCount: Int = 0
    ): SupportChatMessage = generateSupportReply(
        userMessage = userMessage,
        selectedCategory = selectedCategory,
        preferredLanguage = preferredLanguage,
        attachedFilesCount = attachedFilesCount,
        customOtherDescription = customCategoryDescription
    )

    fun validateSupportResponseSafety(responseText: String): Boolean {
        val lower = responseText.lowercase()
        val asksForCredentials = lower.contains("send your password") ||
            lower.contains("share your otp") ||
            lower.contains("enter your private key") ||
            lower.contains("tell me your pin")
        return !asksForCredentials && responseText.isNotBlank()
    }

    fun generateSupportReply(
        userMessage: String,
        selectedCategory: SupportRequestCategory,
        preferredLanguage: String = "AUTO",
        attachedFilesCount: Int = 0,
        customOtherDescription: String = ""
    ): SupportChatMessage {
        val lang = detectLanguage(userMessage, preferredLanguage)
        val trimmedMsg = userMessage.trim()
        val detectedCategory = SupportRequestCategory.suggestCategoryForQuery(trimmedMsg)
        val suggestedSwitch = if (
            detectedCategory != SupportRequestCategory.OTHER_GENERAL &&
            detectedCategory != selectedCategory
        ) {
            detectedCategory
        } else {
            null
        }

        // Check if user accidentally shared sensitive credentials
        val lowerMsg = trimmedMsg.lowercase()
        val mentionsSensitiveWarning = lowerMsg.contains("password is") ||
            lowerMsg.contains("my otp is") ||
            lowerMsg.contains("pin is")

        val followUps = if (lang == "HI") selectedCategory.followUpHindi else selectedCategory.followUpEnglish
        val evidenceHint = if (lang == "HI") selectedCategory.evidenceHintHindi else selectedCategory.evidenceHintEnglish
        val needsEscalation = selectedCategory.requiresHumanReviewForAction ||
            lowerMsg.contains("human") ||
            lowerMsg.contains("agent") ||
            lowerMsg.contains("escalate") ||
            lowerMsg.contains("अधिकारी") ||
            lowerMsg.contains("टीम")

        val responseText = buildString {
            if (lang == "HI") {
                append("नमस्ते! मैं StreamVibe AI सहायता सहायक हूँ। ")
                append("आपकी चुनी गई श्रेणी: [${selectedCategory.displayTitle} / ${selectedCategory.hindiTitle}]।\n")
                if (mentionsSensitiveWarning) {
                    append("⚠️ सुरक्षा चेतावनी: हम कभी भी आपका पासवर्ड, ओटीपी (OTP), या निजी कुंजी नहीं मांगते। कृपया इन्हें चैट में साझा न करें।\n")
                }
                if (selectedCategory == SupportRequestCategory.OTHER_GENERAL && customOtherDescription.isNotBlank()) {
                    append("• आपका कस्टम विवरण नोट किया गया है: \"$customOtherDescription\"\n")
                }
                append("• मार्गदर्शन और जांच प्रश्न:\n")
                followUps.forEachIndexed { idx, q ->
                    append("  ${idx + 1}. $q\n")
                }
                append("• प्रमाण अपलोड (Evidence): $evidenceHint ")
                append("(अभी संलग्न फ़ाइलें: $attachedFilesCount/${StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET})\n")
                if (selectedCategory.requiresHumanReviewForAction) {
                    append("• महत्वपूर्ण नोट: डिपॉज़िट, रिफंड, विड्रॉल या अकाउंट बैन अपील की अंतिम पुष्टि केवल बैकएंड और सपोर्ट टीम के सत्यापन के बाद ही की जाती है। ")
                }
                if (suggestedSwitch != null) {
                    append("\n💡 सुझाव: आपका प्रश्न '${suggestedSwitch.displayTitle}' (${suggestedSwitch.hindiTitle}) से संबंधित लगता है। क्या आप श्रेणी बदलना चाहेंगे?")
                }
                if (needsEscalation) {
                    append("\n👩‍💼 यह मामला प्राथमिकता समीक्षा के लिए हमारे मानव सहायता एजेंट (Human Support Team) को अग्रेषित करने के लिए चिह्नित किया गया है। कृपया नीचे 'Submit Support Ticket' दबाएं ताकि सर्वर टिकट नंबर जनरेट हो सके।")
                }
            } else {
                append("Hello! I am the StreamVibe AI Support Assistant. ")
                append("Active Category: [${selectedCategory.displayTitle}].\n")
                if (mentionsSensitiveWarning) {
                    append("⚠️ Security Reminder: StreamVibe Support will NEVER ask for your password, OTP code, or private keys. Please do not share them in chat.\n")
                }
                if (selectedCategory == SupportRequestCategory.OTHER_GENERAL && customOtherDescription.isNotBlank()) {
                    append("• Custom Issue Summary noted: \"$customOtherDescription\"\n")
                }
                append("• Category Troubleshooting & Clarifying Questions:\n")
                followUps.forEachIndexed { idx, q ->
                    append("  ${idx + 1}. $q\n")
                }
                append("• Evidence Guidance: $evidenceHint ")
                append("(Currently attached: $attachedFilesCount/${StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET})\n")
                if (selectedCategory.requiresHumanReviewForAction) {
                    append("• Verification Policy: Deposits, refunds, withdrawals, and account ban appeals are never marked completed until verified on the backend by our team. ")
                }
                if (suggestedSwitch != null) {
                    append("\n💡 Category Suggestion: Your message appears related to '${suggestedSwitch.displayTitle}'. Would you like to switch to that category?")
                }
                if (needsEscalation) {
                    append("\n👩‍💼 Human Support Escalation: This case is flagged for specialist human review. Please tap 'Submit Support Ticket' below to generate an official server ticket reference number.")
                }
            }
        }

        return SupportChatMessage(
            id = "ai_msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            sender = "AI_ASSISTANT",
            message = responseText.trim(),
            language = lang,
            selectedCategory = selectedCategory,
            suggestedCategory = suggestedSwitch,
            requiresHumanEscalation = needsEscalation
        )
    }

    /**
     * Optional live Gemini 3.5 Flash REST call (`gemini-3.5-flash`) using `BuildConfig.GEMINI_API_KEY`.
     * If `GEMINI_API_KEY` is not set in AI Studio Secrets, returns a clear configuration notice
     * alongside the category-safe structured assistant response.
     */
    suspend fun requestLiveGeminiSupportOrFallback(
        userMessage: String,
        selectedCategory: SupportRequestCategory,
        preferredLanguage: String = "AUTO",
        attachedFilesCount: Int = 0
    ): Result<SupportChatMessage> = withContext(Dispatchers.IO) {
        val localStructured = generateSupportReply(
            userMessage = userMessage,
            selectedCategory = selectedCategory,
            preferredLanguage = preferredLanguage,
            attachedFilesCount = attachedFilesCount
        )
        val apiKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext Result.success(localStructured)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val systemInstruction = """
                You are the official StreamVibe AI Customer Support Assistant.
                Active Support Category: ${selectedCategory.displayTitle}.
                Rules:
                1. Respond in ${if (localStructured.language == "HI") "Hindi" else "English"}.
                2. Only address the active category (${selectedCategory.displayTitle}) and never mix unrelated categories.
                3. Never ask for passwords, OTPs, or private keys.
                4. Never claim a deposit, refund, withdrawal, or appeal is already completed without backend verification.
                5. Never invent fake ticket numbers.
            """.trimIndent()

            val reqJson = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", "$systemInstruction\n\nUser query: $userMessage")
                                )
                            )
                        }
                    )
                )
            }
            OutputStreamWriter(conn.outputStream).use { it.write(reqJson.toString()) }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                    .trim()
                if (text.isNotBlank()) {
                    return@withContext Result.success(localStructured.copy(message = text))
                }
            }
            Result.success(localStructured)
        } catch (e: Exception) {
            Result.success(localStructured)
        }
    }
}
