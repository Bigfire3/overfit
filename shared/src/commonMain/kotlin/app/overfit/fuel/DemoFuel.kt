package app.overfit.fuel

import kotlinx.serialization.json.JsonNull

/**
 * Local stand-in for the backend: no network, no real data. Powers "Demo ansehen" so every credit screen can be tried
 * before the server exists. Nothing here touches the real account.
 */
class DemoFuelService(private val nowSec: () -> Long = { 1_794_052_800 }) : FuelService {
    override val isDemo = true

    private val entries = listOf(
        // Opus was overloaded, Sonnet answered (fallback pro → standard); never more than the chosen model is charged.
        LedgerEntry(5, nowSec() - 1_800, "usage", -2_609, "claude-sonnet-5-5", "pro", 1_000, 0, 0, 500, fallback = true),
        LedgerEntry(4, nowSec() - 3_600, "usage", -2_609, "claude-sonnet-5-5", "standard", 1_000, 0, 0, 500),
        LedgerEntry(3, nowSec() - 90_000, "usage", -10_412, "claude-opus-5-5", "pro", 1_600, 0, 0, 720),
        LedgerEntry(2, nowSec() - 180_000, "usage", -261, "claude-haiku-5-5", "lite", 1_000, 0, 0, 500),
        LedgerEntry(1, nowSec() - 400_000, "purchase", 4_215_891, category = "paid"),
    )

    override suspend fun config() = FuelConfig(
        markupBp = 2_800,
        tiers = mapOf(
            "standard" to FuelTierInfo(true, "claude-sonnet-5-5", true, TierPrice(1_840_000, 9_200_000), "Claude Sonnet 5.5", "Anthropic"),
            "pro" to FuelTierInfo(true, "claude-opus-5-5", true, TierPrice(3_680_000, 18_400_000), "Claude Opus 5.5", "Anthropic"),
            "lite" to FuelTierInfo(true, "claude-haiku-5-5", true, TierPrice(92_000, 460_000), "Claude Haiku 5.5", "Anthropic"),
        ),
        packages = listOf(
            FuelPackage("overfit.fuel.5", 6_990_000, 5_000_000),
            FuelPackage("overfit.fuel.10", 13_990_000, 10_000_000),
        ),
    )

    override suspend fun account() = FuelAccount(
        accountId = "demo",
        balanceMicro = DEMO_BALANCE_MICRO,
        hasRecoveryCode = false,
    )

    override suspend fun ledger(before: Long?, limit: Int) = LedgerPage(entries.filter { before == null || it.id < before }.take(limit))

    override suspend fun createRecoveryCode() = "DEMO-CODE-2468-WXYZ-ABCD-EFGH"

    override suspend fun restore(code: String): FuelAccount = account()

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
