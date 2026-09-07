package com.functy.autofewards.core

import java.security.MessageDigest
import java.util.UUID

/**
 * 米游社 device_id / device_fp 生成（Flutter 版 crypto/uuid_v3.dart + Md5Like 翻译）。
 *
 * - uuid3（MD5 命名空间版，NAMESPACE_URL）：deviceId = uuid3(stoken + stuid)
 * - 伪 device_fp：真 fp 由设备指纹接口下发，这里用 md5(seed) 拼成 40 hex 的
 *   稳定替代值（服务端多数场景允许缺省，带上可提高接口通过率）。
 */
object MihoyoIds {
    private val NS_URL_BYTES = byteArrayOf(
        0x6b, 0xa7.toByte(), 0xb8.toByte(), 0x11,
        0x9d.toByte(), 0xad.toByte(), 0x11, 0xd1.toByte(),
        0x80.toByte(), 0xb4.toByte(), 0x00, 0xc0.toByte(),
        0x4f, 0xd4.toByte(), 0x30, 0xc8.toByte(),
    )

    /** uuid3(uuid.NAMESPACE_URL, name)。 */
    fun uuidV3(name: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(NS_URL_BYTES)
        val hash = digest.digest(name.toByteArray(Charsets.UTF_8)).copyOf(16)
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x30).toByte() // version 3
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80.toByte().toInt()).toByte() // variant
        val hex = hash.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-" +
            "${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    /** 稳定 device_id：uuid3(stoken + stuid)。 */
    fun deviceIdFrom(stoken: String, stuid: String): String = uuidV3(stoken + stuid)

    /** 伪 device_fp：md5(seed) + 前 8 位 = 40 hex。 */
    fun deviceFp(seed: String): String {
        val h = md5Hex(seed.toByteArray(Charsets.UTF_8))
        return h + h.substring(0, 8)
    }

    private fun md5Hex(data: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }

    /** 随机 UUID v4 形状（扫码会话 deviceId 用）。 */
    fun randomUuid4(): String = UUID.randomUUID().toString()
}
