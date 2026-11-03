package app.overfit.fuel

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Client of the credit/invite backend (docs/backend-api.md). Signs every request with the device key.
 * Recovers on its own from `unknown_device` (registers, then retries once) and from clock skew
 * (`timestamp_out_of_range` carries the server time).
 */
class FuelApi(
    http: HttpClient,
    baseUrl: String,
    private val signer: RequestSigner,
    private val attestor: Attestor = NoAttestor,
    private val nowSec: () -> Long = { Clock.System.now().epochSeconds },
    private val nonce: () -> String = ::randomNonce,
) : FuelService, SignedTransport {
    private val base = baseUrl.trim().trimEnd('/')
    private val http = http.config { install(HttpTimeout) { requestTimeoutMillis = 130_000 } }
    private val json = Json { ignoreUnknownKeys = true }
    private var skewSec = 0L
    private val registerLock = Mutex()
    private var registered = false

    override val isDemo = false

    // --- Plumbing ---------------------------------------------------------------------------------------------

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: String? = null,
        signed: Boolean = true,
        rawBody: ByteArray? = null,
        rawType: ContentType = ContentType.Application.Json,
    ): HttpResponse {
        val bytes = rawBody ?: body?.encodeToByteArray() ?: ByteArray(0)
        val build: HttpRequestBuilder.() -> Unit = {
            this.method = method
            if (signed) {
                val h = signer.sign(method.value, path, bytes, nowSec() + skewSec, nonce())
                header(FuelHeaders.DEVICE, h.device)
                header(FuelHeaders.TIMESTAMP, h.timestamp)
                header(FuelHeaders.NONCE, h.nonce)
                header(FuelHeaders.SIGNATURE, h.signature)
            }
            if (body != null || rawBody != null) {
                contentType(if (rawBody != null) rawType else ContentType.Application.Json)
                setBody(bytes)
            }
            header(HttpHeaders.Accept, "application/json")
        }
        return try {
            http.request(base + path, build)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FuelException("network", message = e.message)
        }
    }

    private suspend fun call(
        method: HttpMethod,
        path: String,
        body: String? = null,
        rawBody: ByteArray? = null,
        rawType: ContentType = ContentType.Application.Json,
    ): HttpResponse {
        ensureRegistered()
        var response = send(method, path, body, rawBody = rawBody, rawType = rawType)
        for (attempt in 0 until 2) {
            if (response.status.value != 401) break
            val error = parseError(response)
            when (error.code) {
                "unknown_device" -> { registered = false; ensureRegistered(force = true) }
                "timestamp_out_of_range" -> {
                    val server = error.details["serverTime"] ?: break
                    skewSec = server - nowSec()
                }
                else -> break
            }
            response = send(method, path, body, rawBody = rawBody, rawType = rawType)
        }
        return response
    }

    private suspend fun <T> parse(response: HttpResponse, read: (JsonElement) -> T): T {
        if (response.status.value !in 200..299) throw parseError(response)
        val text = response.bodyAsText()
        return try {
            read(json.parseToJsonElement(text))
        } catch (e: Exception) {
            throw FuelException("bad_response", response.status.value, message = e.message)
        }
    }

    private suspend fun parseError(response: HttpResponse): FuelException {
        val status = response.status.value
        val obj = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = obj?.get("code")?.jsonPrimitive?.contentOrNull ?: "http_$status"
        val details = (obj?.get("details") as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }?.toMap().orEmpty()
        return FuelException(code, status, details, obj?.get("message")?.jsonPrimitive?.contentOrNull)
    }

    private suspend fun ensureRegistered(force: Boolean = false) {
        if (registered && !force) return
        registerLock.withLock {
            if (registered && !force) return
            val body = buildJsonObject { put("publicKey", signer.publicKeyBase64Url()) }.toString()
            parse(send(HttpMethod.Post, "/v1/devices", body)) { }
            registered = true
        }
    }

    override suspend fun request(method: String, path: String, body: ByteArray?, contentType: String): String =
        parse(call(HttpMethod.parse(method), path, rawBody = body, rawType = ContentType.parse(contentType))) { it.toString() }

    private inline fun <reified T> decode(element: JsonElement): T = json.decodeFromJsonElement(element)

    // --- Endpoints --------------------------------------------------------------------------------------------

    override suspend fun config(): FuelConfig = parse(send(HttpMethod.Get, "/v1/config", signed = false)) { decode(it) }

    override suspend fun account(): FuelAccount = parse(call(HttpMethod.Get, "/v1/account")) { decode(it) }

    override suspend fun setDisplayName(name: String?): String? {
        val body = buildJsonObject { put("displayName", name?.trim()?.takeIf { it.isNotEmpty() }) }.toString()
        return parse(call(HttpMethod.Put, "/v1/account/profile", body)) { it.jsonObject["displayName"]?.jsonPrimitive?.contentOrNull }
    }

    override suspend fun ledger(before: Long?, limit: Int): LedgerPage {
        val query = buildString { append("?limit=").append(limit); if (before != null) append("&before=").append(before) }
        return parse(call(HttpMethod.Get, "/v1/account/ledger$query")) { decode(it) }
    }

    override suspend fun createRecoveryCode(): String =
        parse(call(HttpMethod.Post, "/v1/recovery/code")) { decode<RecoveryCode>(it).code }

    override suspend fun restore(code: String): FuelAccount {
        val body = buildJsonObject { put("code", code.trim()) }.toString()
        return parse(call(HttpMethod.Post, "/v1/recovery/restore", body)) { decode(it) }
    }

    override suspend fun spotToken(): SpotToken = parse(call(HttpMethod.Post, "/v1/spot/token")) { decode(it) }

    override suspend fun redeemSpot(token: String): SpotRedeemResult {
        val challenge = parse(call(HttpMethod.Post, "/v1/spot/challenge")) { decode<SpotChallenge>(it) }
        // Test server (DEPLOY_MODE=test): no device check, a test token bound to this device instead. Production never accepts it.
        val attestation = attestor.attest(challenge.challenge)
            ?: if (config().mode == "test") Attestation("google", "test:${signer.deviceId}") else null
            ?: throw FuelException("attestation_unavailable", 503)
        val body = buildJsonObject {
            put("token", token)
            put("attestation", buildJsonObject {
                put("platform", attestation.platform)
                put("token", attestation.token)
                put("challenge", challenge.challenge)
            })
        }.toString()
        return parse(call(HttpMethod.Post, "/v1/spot/redeem", body)) { decode(it) }
    }

    override suspend fun webPurchaseLink(locale: String): WebPurchaseLink {
        val body = buildJsonObject { put("locale", locale) }.toString()
        return parse(call(HttpMethod.Post, "/v1/purchase/web-link", body)) { decode(it) }
    }

    override suspend fun redeemCode(code: String): CodeRedeemResult {
        val body = buildJsonObject { put("code", code.trim()) }.toString()
        return parse(call(HttpMethod.Post, "/v1/codes/redeem", body)) { decode(it) }
    }

    override suspend fun messages(bodyJson: String): FuelMessageResult = parse(call(HttpMethod.Post, "/v1/messages", bodyJson)) { element ->
        val meta = element.jsonObject["overfit"]?.jsonObject
        FuelMessageResult(
            body = element,
            costMicro = meta?.get("costMicro")?.jsonPrimitive?.longOrNull ?: 0,
            balanceMicro = meta?.get("balanceMicro")?.jsonPrimitive?.longOrNull ?: 0,
            servedModel = meta?.get("model")?.jsonPrimitive?.contentOrNull,
            chosenModel = meta?.get("requestedModel")?.jsonPrimitive?.contentOrNull,
        )
    }
}

/** 16 random bytes as base64url (22 characters, matches `[A-Za-z0-9_-]{16,64}`). */
fun randomNonce(): String = Random.nextBytes(16).toBase64Url()
