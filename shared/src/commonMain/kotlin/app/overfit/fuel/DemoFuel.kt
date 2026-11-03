package app.overfit.fuel

import kotlinx.serialization.json.JsonNull

/**
 * Local stand-in for the backend: no network, no real data. Powers "Demo ansehen" so every credit/invite screen
 * can be tried before the server exists. Nothing here touches the real account.
 */
class DemoFuelService(private val nowSec: () -> Long = { 1_762_128_000 }) : FuelService {
    override val isDemo = true

    private var name: String? = "Fabian"
    private var tokenCounter = 0

    private val entries = listOf(
        LedgerEntry(6, nowSec() - 1_800, "usage", -2_609, "claude-sonnet-5-5", "standard", 1_000, 0, 0, 500, fallback = true),
        LedgerEntry(5, nowSec() - 3_600, "usage", -2_609, "gemini-3.6-flash", "standard", 1_000, 0, 0, 500),
        LedgerEntry(4, nowSec() - 90_000, "usage", -10_412, "claude-sonnet-5-5", "pro", 1_600, 0, 0, 720),
        LedgerEntry(3, nowSec() - 180_000, "invite_reward", 0, reviews = 5, category = "gifted"),
        LedgerEntry(2, nowSec() - 400_000, "purchase", 3_200_000, category = "paid"),
        LedgerEntry(1, nowSec() - 900_000, "test_credit", 1_000_000, category = "gifted"),
    )

    override suspend fun config() = FuelConfig(
        markupBp = 2_800,
        tiers = mapOf(
            "standard" to FuelTierInfo(true, "gemini-3.6-flash", true, TierPrice(690_000, 3_450_000), "Gemini 3.6 Flash", "Google", 1_000),
            "pro" to FuelTierInfo(true, "claude-sonnet-5-5", true, TierPrice(1_840_000, 9_200_000), "Claude Sonnet 5.5", "Anthropic"),
            "lite" to FuelTierInfo(true, "@cf/meta/llama-3.1-8b-instruct", false, TierPrice(259_153, 760_488), "Llama 3.1 8B", "Cloudflare"),
        ),
        packages = listOf(
            FuelPackage("overfit.fuel.5", 6_990_000, 5_000_000),
            FuelPackage("overfit.fuel.10", 13_990_000, 10_000_000),
        ),
    )

    override suspend fun account() = FuelAccount(
        accountId = "demo",
        balanceMicro = DEMO_BALANCE_MICRO,
        purchasedMicro = DEMO_BALANCE_MICRO - 1_000_000,
        giftedMicro = 1_000_000,
        expiring = listOf(ExpiringItem(1_000_000, nowSec() + 150L * 86_400)),
        giftReviews = GiftReviews(5, listOf(GiftReviewItem(5, nowSec() + 170L * 86_400))),
        displayName = name,
        hasRecoveryCode = false,
        invite = InviteStatus(invitedThisMonth = 2, monthlyLimit = 10, redeemedStarter = false),
    )

    override suspend fun setDisplayName(name: String?): String? {
        this.name = name?.trim()?.takeIf { it.isNotEmpty() }
        return this.name
    }

    override suspend fun ledger(before: Long?, limit: Int) = LedgerPage(entries.filter { before == null || it.id < before }.take(limit))

    override suspend fun createRecoveryCode() = "DEMO-CODE-2468-WXYZ-ABCD-EFGH"

    override suspend fun restore(code: String): FuelAccount = account()

    override suspend fun inviteToken() = InviteToken("demo-invite-token-${++tokenCounter}", nowSec() + 45, 45)

    override suspend fun redeemInvite(token: String) =
        InviteRedeemResult(
            grantedReviews = 5, expiresAt = nowSec() + 180L * 86_400, inviter = Inviter("Fabian"), balanceMicro = 0,
            giftReviews = GiftReviews(5, listOf(GiftReviewItem(5, nowSec() + 180L * 86_400))),
        )

    /** Demo: any well-formed code works once per process; nothing touches a real account. */
    override suspend fun redeemCode(code: String): CodeRedeemResult {
        if (code.filter { it.isLetterOrDigit() }.length != 12) throw FuelException("invalid_code", 404)
        if (!redeemed.add(code.uppercase())) throw FuelException("already_redeemed", 409)
        return CodeRedeemResult(5_000_000, DEMO_BALANCE_MICRO + 5_000_000)
    }

    private val redeemed = mutableSetOf<String>()

    override suspend fun messages(bodyJson: String): FuelMessageResult =
        FuelMessageResult(JsonNull, 0, DEMO_BALANCE_MICRO)

    companion object {
        const val DEMO_BALANCE_MICRO = 4_200_000L
    }
}
