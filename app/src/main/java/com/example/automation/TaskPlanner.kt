package com.example.automation

import com.example.accessibility.AccessibilityController
import com.example.ai.GeminiEngine
import com.example.ai.GeminiPlanResult
import com.example.memory.MemoryManager

sealed class PlanningResult {
    data class Success(
        val actions: List<Action>,
        val spokenResponse: String,
        val isGeminiGenerated: Boolean
    ) : PlanningResult()

    data class Conversational(
        val spokenResponse: String
    ) : PlanningResult()

    data class Unsupported(
        val capability: String,
        val spokenResponse: String
    ) : PlanningResult()

    data class Error(
        val message: String,
        val isKeyMissing: Boolean = false,
        val errorType: com.example.ai.GeminiErrorType = com.example.ai.GeminiErrorType.UNKNOWN
    ) : PlanningResult()
}

class TaskPlanner(
    private val geminiEngine: GeminiEngine,
    private val memoryManager: MemoryManager,
    private val actionValidator: ActionValidator,
    private val accessibilityController: AccessibilityController
) {

    suspend fun plan(rawPrompt: String): PlanningResult {
        // 1. Expand aliases from local memory (e.g. "YT" -> "YouTube")
        val expandedPrompt = memoryManager.expandPromptWithAliases(rawPrompt)

        // 2. Try planning via Gemini Engine if available
        val activePkg = accessibilityController.getActivePackage()
        val visibleTexts = if (accessibilityController.isAccessibilityActive()) {
            accessibilityController.readScreenContent()
        } else {
            emptyList()
        }

        val memoryContext = memoryManager.formatMemoryContextForAi(limit = 10)

        val geminiResult = geminiEngine.planActions(
            userPrompt = expandedPrompt,
            activeAppPackage = activePkg,
            screenVisibleTexts = visibleTexts,
            memoryContext = memoryContext
        )

        when (geminiResult) {
            is GeminiPlanResult.Success -> {
                if (geminiResult.unsupportedCapability != null) {
                    return PlanningResult.Unsupported(
                        capability = geminiResult.unsupportedCapability,
                        spokenResponse = geminiResult.spokenResponse.ifBlank {
                            "The capability to ${geminiResult.unsupportedCapability} is not available yet in NEX."
                        }
                    )
                }

                if (geminiResult.actions.isEmpty()) {
                    return PlanningResult.Conversational(
                        spokenResponse = geminiResult.spokenResponse.ifBlank { "Acknowledged." }
                    )
                }

                val validation = actionValidator.validatePlan(geminiResult.actions)
                val invalid = validation.filter { !it.second.isValid }
                if (invalid.isNotEmpty()) {
                    // Fall back to local deterministic planner if gemini produced invalid schema
                    val localFallback = tryLocalDeterministicPlan(expandedPrompt)
                    if (localFallback != null) {
                        return localFallback
                    }
                    val reasons = invalid.joinToString("; ") { "${it.first.type}: ${it.second.reason}" }
                    return PlanningResult.Error("Plan validation rejected: $reasons")
                }

                return PlanningResult.Success(
                    actions = geminiResult.actions,
                    spokenResponse = geminiResult.spokenResponse,
                    isGeminiGenerated = true
                )
            }

            is GeminiPlanResult.Error -> {
                // If Gemini key is unconfigured or network failed, use deterministic local fallback
                val localPlan = tryLocalDeterministicPlan(expandedPrompt)
                if (localPlan != null) {
                    return localPlan
                }

                return PlanningResult.Error(
                    message = geminiResult.message,
                    isKeyMissing = geminiResult.isKeyMissing,
                    errorType = geminiResult.errorType
                )
            }
        }
    }

    fun tryLocalDeterministicPlan(prompt: String): PlanningResult? {
        val clean = prompt.trim().lowercase()
            .removePrefix("hey nex,").removePrefix("hey nex")
            .removePrefix("nex,").removePrefix("nex")
            .removePrefix("হে নেক্স,").removePrefix("হে নেক্স")
            .removePrefix("নেক্স,").removePrefix("নেক্স")
            .removeSuffix("hey nex").removeSuffix("nex")
            .removeSuffix("নেক্স")
            .trim()

        // Conversational greetings
        if (clean == "hello" || clean == "hi" || clean.isBlank() || clean.startsWith("hello") || clean.startsWith("hi")) {
            return PlanningResult.Conversational("Hello! NEX device automation engine is active and ready for your commands.")
        }
        if (clean.contains("কেমন আছো") || clean.contains("নমস্কার") || clean.contains("হ্যালো")) {
            return PlanningResult.Conversational("হ্যালো! আমি নেক্স। আমি আপনার ডিভাইসে যেকোনো অটোমেশন চালাতে প্রস্তুত।")
        }
        if (clean == "what can you do" || clean == "help" || clean == "কী করতে পারো") {
            return PlanningResult.Conversational("I can open apps, search videos, adjust device settings, scroll, tap elements, type text, and execute compound device actions.")
        }

        // 1. Benchmark: "Open YouTube and search for [query]" / "ইউটিউব খুলে [query] সার্চ করো"
        val ytCompoundEn = Regex("(?:open|launch)\\s+youtube\\s+(?:and|then)?\\s*(?:search\\s+(?:for\\s+)?(?:a\\s+video\\s+about\\s+)?|find\\s+)(.+)", RegexOption.IGNORE_CASE).find(prompt.trim())
        val ytCompoundBn = Regex("(?:ইউটিউব|youtube)\\s+(?:খুলে|ওপেন\\s+করে|চালিয়ে)?\\s*(.+?)\\s*(?:সার্চ\\s+করো|খুঁজে\\s+দাও|ভিডিও\\s+সার্চ\\s+করো)", RegexOption.IGNORE_CASE).find(prompt.trim())

        val ytQuery = ytCompoundEn?.groupValues?.get(1)?.trim() ?: ytCompoundBn?.groupValues?.get(1)?.trim()
        if (!ytQuery.isNullOrBlank()) {
            val actions = listOf(
                Action(
                    type = ActionType.OPEN_APP,
                    parameters = ActionParameters(appName = "YouTube", packageName = "com.google.android.youtube"),
                    description = "Open YouTube application"
                ),
                Action(
                    type = ActionType.WAIT,
                    parameters = ActionParameters(durationMs = 800L),
                    description = "Wait for YouTube to render"
                ),
                Action(
                    type = ActionType.SEARCH,
                    parameters = ActionParameters(query = ytQuery, packageName = "com.google.android.youtube"),
                    description = "Search YouTube for \"$ytQuery\""
                )
            )
            return PlanningResult.Success(
                actions = actions,
                spokenResponse = "Opening YouTube and searching for \"$ytQuery\".",
                isGeminiGenerated = false
            )
        }

        // 2. Benchmark: "Open Settings and show me the Wi-Fi settings" / "সেটিংস খুলে ওয়াইফাই দেখাও" / "ওয়াইফাই সেটিংস খোলো"
        if (clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("ওয়াইফাই") || clean.contains("ওয়াই-ফাই")) {
            if (clean.contains("setting") || clean.contains("সেটিংস") || clean.contains("show") || clean.contains("open") || clean.contains("খোলো") || clean.contains("দেখাও")) {
                val actions = listOf(
                    Action(
                        type = ActionType.OPEN_APP,
                        parameters = ActionParameters(appName = "Settings", targetText = "wifi"),
                        description = "Open Wi-Fi Settings"
                    )
                )
                return PlanningResult.Success(
                    actions = actions,
                    spokenResponse = "Opening Wi-Fi settings.",
                    isGeminiGenerated = false
                )
            }
        }

        if (clean.contains("bluetooth") || clean.contains("ব্লুটুথ")) {
            val actions = listOf(
                Action(
                    type = ActionType.OPEN_APP,
                    parameters = ActionParameters(appName = "Settings", targetText = "bluetooth"),
                    description = "Open Bluetooth Settings"
                )
            )
            return PlanningResult.Success(
                actions = actions,
                spokenResponse = "Opening Bluetooth settings.",
                isGeminiGenerated = false
            )
        }

        // 3. Benchmark: "Open Chrome and search for [query]" / "ক্রোম ওপেন করে সার্চ করো [query]"
        val chromeSearchMatch = Regex("(?:ক্রোম|chrome)\\s+(?:open|ওপেন|খুলে)?\\s*(?:করে|এ)?\\s*(?:search\\s+করো|সার্চ\\s+করো|খুঁজো)[:\\s]*(.+)", RegexOption.IGNORE_CASE).find(prompt.trim())
            ?: Regex("(?:open|launch)?\\s*chrome\\s+(?:and|then)?\\s*search\\s+(?:for)?[:\\s]*(.+)", RegexOption.IGNORE_CASE).find(prompt.trim())
        val chromeQuery = chromeSearchMatch?.groupValues?.get(1)?.trim()
        if (!chromeQuery.isNullOrBlank()) {
            val actions = listOf(
                Action(
                    type = ActionType.SEARCH,
                    parameters = ActionParameters(query = chromeQuery, packageName = "com.android.chrome"),
                    description = "Search Chrome for \"$chromeQuery\""
                )
            )
            return PlanningResult.Success(
                actions = actions,
                spokenResponse = "Searching Chrome for \"$chromeQuery\".",
                isGeminiGenerated = false
            )
        }

        // 3b. Benchmark: "Open Chrome and navigate to [website]" / "ক্রোম ওপেন করে [url] এ যাও"
        val chromeCompoundEn = Regex("(?:open|launch)\\s+chrome\\s+(?:and|then)?\\s*(?:navigate\\s+to\\s+|go\\s+to\\s+|open\\s+)(.+)", RegexOption.IGNORE_CASE).find(clean)
        val chromeCompoundBn = Regex("(?:ক্রোম|chrome)\\s+(?:ওপেন\\s+করে|খুলে)?\\s*(.+?)\\s*(?:এ\\s*যাও|ওয়েবসাইটে\\s*যাও|ভিজিট\\s*করো)", RegexOption.IGNORE_CASE).find(clean)

        val webTarget = chromeCompoundEn?.groupValues?.get(1)?.trim() ?: chromeCompoundBn?.groupValues?.get(1)?.trim()
        if (!webTarget.isNullOrBlank()) {
            val url = if (webTarget.startsWith("http://") || webTarget.startsWith("https://")) {
                webTarget
            } else if (webTarget.contains(".com") || webTarget.contains(".org") || webTarget.contains(".net") || webTarget.contains(".io")) {
                "https://$webTarget"
            } else {
                webTarget
            }

            val actions = listOf(
                Action(
                    type = ActionType.OPEN_APP,
                    parameters = ActionParameters(appName = "Chrome", packageName = "com.android.chrome"),
                    description = "Open Google Chrome"
                ),
                Action(
                    type = ActionType.SEARCH,
                    parameters = ActionParameters(query = url, packageName = "com.android.chrome"),
                    description = "Navigate Chrome to \"$url\""
                )
            )
            return PlanningResult.Success(
                actions = actions,
                spokenResponse = "Opening Chrome and navigating to $webTarget.",
                isGeminiGenerated = false
            )
        }

        // 4. Benchmark: "Scroll down" / "নিচে স্ক্রোল করো"
        if (clean.contains("scroll down") || clean.contains("নিচে স্ক্রোল") || clean == "scroll" || clean == "স্ক্রোল করো") {
            val actions = listOf(
                Action(
                    type = ActionType.SCROLL,
                    parameters = ActionParameters(direction = "DOWN"),
                    description = "Scroll down"
                )
            )
            return PlanningResult.Success(actions, "Scrolling down.", isGeminiGenerated = false)
        }

        // 5. Benchmark: "Scroll up" / "উপরে স্ক্রোল করো"
        if (clean.contains("scroll up") || clean.contains("উপরে স্ক্রোল")) {
            val actions = listOf(
                Action(
                    type = ActionType.SCROLL_BACKWARD,
                    parameters = ActionParameters(direction = "UP"),
                    description = "Scroll up"
                )
            )
            return PlanningResult.Success(actions, "Scrolling up.", isGeminiGenerated = false)
        }

        // 6. Benchmark: "Open the first result" / "প্রথম রেজাল্টটা খোলো" / "প্রথম ভিডিওটি চালাও"
        if (clean.contains("first result") || clean.contains("open the first") || clean.contains("প্রথম রেজাল্ট") || clean.contains("প্রথম ভিডিও")) {
            val actions = listOf(
                Action(
                    type = ActionType.TAP,
                    parameters = ActionParameters(targetText = "first result"),
                    description = "Tap the first result on screen"
                )
            )
            return PlanningResult.Success(actions, "Opening the first result.", isGeminiGenerated = false)
        }

        // 7. Benchmark: "Type this text into the current text field" / "এই লেখাটা টাইপ করো: [text]" / "type [text]"
        val typeMatchEn = Regex("^(?:type\\s+(?:this\\s+text\\s+(?:into\\s+(?:the\\s+)?(?:current\\s+)?text\\s+field)?|text\\s+)?)(.+)$", RegexOption.IGNORE_CASE).find(clean)
        val typeMatchBn = Regex("(?:এই\\s+লেখাটা\\s+)?টাইপ\\s+করো[:\\s]*(.+)", RegexOption.IGNORE_CASE).find(clean)

        if (clean.startsWith("type ") || clean.startsWith("টাইপ করো") || clean.contains("text field")) {
            val textToType = typeMatchEn?.groupValues?.get(1)?.trim()
                ?: typeMatchBn?.groupValues?.get(1)?.trim()
                ?: "Hello from NEX"
            val actions = listOf(
                Action(
                    type = ActionType.TYPE_TEXT,
                    parameters = ActionParameters(inputText = textToType),
                    description = "Type \"$textToType\" into active field"
                )
            )
            return PlanningResult.Success(actions, "Typing text into field.", isGeminiGenerated = false)
        }

        // 8. Benchmark: "Go back" / "পিছনে যাও" / "ব্যাক করো"
        if (clean == "back" || clean == "go back" || clean.contains("পিছনে যাও") || clean.contains("ব্যাক করো")) {
            val actions = listOf(
                Action(
                    type = ActionType.BACK,
                    description = "Navigate back to previous screen"
                )
            )
            return PlanningResult.Success(actions, "Going back.", isGeminiGenerated = false)
        }

        // 9. Benchmark: "Go home" / "হোমে যাও"
        if (clean == "home" || clean == "go home" || clean.contains("হোম এ যাও") || clean.contains("হোমে যাও")) {
            val actions = listOf(
                Action(
                    type = ActionType.HOME,
                    description = "Navigate to device Home screen"
                )
            )
            return PlanningResult.Success(actions, "Going to Home screen.", isGeminiGenerated = false)
        }

        // 10. Benchmark: Simple Open App: "Open [app]" / "[app] ওপেন করো" / "[app] খোলো"
        val bnOpenMatch = Regex("(.+?)\\s*(?:এ\\s*যাও|তে\\s*যাও|যাও|ওপেন\\s*করো|খুলো|খোলো|চালু\\s*করো)$", RegexOption.IGNORE_CASE).find(clean)
        if (bnOpenMatch != null) {
            var target = bnOpenMatch.groupValues[1].trim()
            target = when (target.lowercase()) {
                "ইউটিউব", "youtube" -> "YouTube"
                "সেটিংস", "settings" -> "Settings"
                "ক্রোম", "chrome" -> "Chrome"
                "ক্যামেরা", "camera" -> "Camera"
                "ক্যালকুলেটর", "calculator" -> "Calculator"
                "ম্যাপস", "maps" -> "Google Maps"
                else -> target
            }
            val actions = listOf(
                Action(
                    type = ActionType.OPEN_APP,
                    parameters = ActionParameters(appName = target),
                    description = "Open $target"
                )
            )
            return PlanningResult.Success(actions, "$target ওপেন করা হচ্ছে।", isGeminiGenerated = false)
        }

        val openMatch = Regex("^(?:open|launch|start|go to)\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)
        if (openMatch != null) {
            var appTarget = openMatch.groupValues[1].trim()
            if (appTarget.isNotBlank()) {
                appTarget = when (appTarget.lowercase()) {
                    "youtube" -> "YouTube"
                    "settings" -> "Settings"
                    "chrome" -> "Chrome"
                    "camera" -> "Camera"
                    "calculator" -> "Calculator"
                    "maps", "google maps" -> "Google Maps"
                    else -> appTarget.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                val actions = listOf(
                    Action(
                        type = ActionType.OPEN_APP,
                        parameters = ActionParameters(appName = appTarget),
                        description = "Open $appTarget"
                    )
                )
                return PlanningResult.Success(actions, "Opening $appTarget.", isGeminiGenerated = false)
            }
        }

        // 11. Benchmark: General Search: "Search for [query]"
        val searchMatch = Regex("^(?:search|google|look up|search for)\\s+(.+)$", RegexOption.IGNORE_CASE).find(clean)
        if (searchMatch != null) {
            val query = searchMatch.groupValues[1].trim()
            if (query.isNotBlank()) {
                val actions = listOf(
                    Action(
                        type = ActionType.SEARCH,
                        parameters = ActionParameters(query = query),
                        description = "Search the web for \"$query\""
                    )
                )
                return PlanningResult.Success(actions, "Searching for $query.", isGeminiGenerated = false)
            }
        }

        return null
    }
}
