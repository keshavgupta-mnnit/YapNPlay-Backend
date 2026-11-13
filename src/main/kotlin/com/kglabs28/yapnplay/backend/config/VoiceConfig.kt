package com.kglabs28.yapnplay.backend.config

/** One fixed TURN server: its turn:/turns: addresses, username and password. */
data class TurnServer(val urls: List<String>, val username: String?, val credential: String?) {
    override fun toString(): String = "TurnServer(urls=$urls, username=$username, credential=${credential?.let { "***" }})"
}

/**
 * TURN relays for peer-to-peer voice, from the environment only (secrets never go in
 * code or git). Without any, voice still works over STUN for most networks. Any
 * number of fixed TURN servers (e.g. ExpressTURN for volume, Metered for port 443),
 * and/or a Cloudflare TURN key (short-lived credentials made on demand). Phones try
 * them all and use whichever gets through.
 */
data class VoiceConfig(
    val fixedTurn: List<TurnServer> = emptyList(),
    val cloudflareKeyId: String? = null,
    val cloudflareApiToken: String? = null,
) {
    val hasCloudflare: Boolean get() = cloudflareKeyId != null && cloudflareApiToken != null

    override fun toString(): String =
        "VoiceConfig(fixedTurn=$fixedTurn, cloudflareKeyId=$cloudflareKeyId, cloudflareApiToken=${cloudflareApiToken?.let { "***" }})"
}
