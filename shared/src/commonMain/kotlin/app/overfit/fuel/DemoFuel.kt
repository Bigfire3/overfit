package app.overfit.fuel

import kotlinx.serialization.json.JsonNull

/**
 * Local stand-in for the backend: no network, no real data. Powers "Demo ansehen" so every Fuel/Spot screen
 * can be tried before the server exists. Nothing here touches the real account.
 */
class DemoFuelService(private val nowSec: () -> Long = { 1_762_128_000 }) : FuelService {
    override val isDemo = true

    private var name: String? = "Fabian"
    private var tokenCounter = 0

    private val entries = listOf(
        LedgerEntry(5, nowSec() - 3_600, "usage", -6_956, "claude-sonnet-5-5", 1_000, 0, 0, 500),
        LedgerEntry(4, nowSec() - 90_000, "usage", -10_412, "claude-sonnet-5-5", 1_600, 0, 0, 720),
        LedgerEntry(3, nowSec() - 180_000, "spot_reward", 1_000_000),
        LedgerEntry(2, nowSec() - 400_000, "purchase", 3_200_000),
        LedgerEntry(1, nowSec() - 900_000, "spot_bonus", 1_000_000),
    )

    override suspend fun config() = FuelConfig(
        markupBp = 800, storeFeeBp = 1_500,
        packages = listOf(
            FuelPackage("overfit.fuel.5", 5_000_000, 3_935_185),
            FuelPackage("overfit.fuel.10", 10_000_000, 7_870_370),
        ),
    )

    override suspend fun account() = FuelAccount(
        accountId = "demo",
        balanceMicro = DEMO_BALANCE_MICRO,
        expiring = listOf(ExpiringItem(1_000_000, nowSec() + 150L * 86_400)),
        displayName = name,
        hasRecoveryCode = false,
        spot = SpotStatus(invitedThisMonth = 2, monthlyLimit = 10, redeemedStarter = false),
    )

    override suspend fun setDisplayName(name: String?): String? {
        this.name = name?.trim()?.takeIf { it.isNotEmpty() }
        return this.name
    }

    override suspend fun ledger(before: Long?, limit: Int) = LedgerPage(entries.filter { before == null || it.id < before }.take(limit))

    override suspend fun createRecoveryCode() = "DEMO-CODE-2468-WXYZ-ABCD-EFGH"

    override suspend fun restore(code: String): FuelAccount = account()

    override suspend fun spotToken() = SpotToken("demo-spot-token-${++tokenCounter}", nowSec() + 45, 45)

    override suspend fun redeemSpot(token: String) =
        SpotRedeemResult(grantedMicro = 1_000_000, expiresAt = nowSec() + 180L * 86_400, inviter = SpotInviter("Fabian"), balanceMicro = 1_000_000)

    override suspend fun messages(bodyJson: String): FuelMessageResult =
        FuelMessageResult(JsonNull, 0, DEMO_BALANCE_MICRO)

    companion object {
        const val DEMO_BALANCE_MICRO = 4_200_000L
    }
}
