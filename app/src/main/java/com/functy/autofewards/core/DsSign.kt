package com.functy.autofewards.core

import java.security.MessageDigest

/**
 * 米游社 DS 签名（Dart 版 ds_sign.dart 的 1:1 Kotlin 翻译）。
 *
 * 算法（逆向结论，见 Flutter 版 docs/REVERSE_REPORT.md）：
 *   DS1（GET 类接口）:
 *     t = 秒级时间戳
 *     r = 从 [a-z0-9] 随机取 6 个字符（sample 语义，不重复）
 *     c = md5("salt={SALT}&t={t}&r={r}")
 *     DS = "{t},{r},{c}"
 *
 *   DS2（POST JSON 类接口，X6 salt）:
 *     t = 秒级时间戳
 *     r = 100001..200000 随机整数
 *     c = md5("salt={SALT_X6}&t={t}&r={r}&b={body}&q={query}")
 *     DS = "{t},{r},{c}"
 *     b=完整请求体字符串（与服务端收到的字节严格一致，不要重新序列化）；
 *     q=URL query 字符串（无 query 时为空串）。
 */
object DsSign {
    /** 与米游社 App 版本对应的 salt（失效时更新这里）。 */
    const val SALT_K2 = "47f15f1b66bee46b816115d8e8e6ebb6"
    const val SALT_WEB = "d9200c846b10886e8c874fc33c8f308b"
    const val SALT_X4 = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs"
    const val SALT_X6 = "t0qEgfub6cvueAPgR5m9aQWWVciEer7v"

    /** MiyoQian 配对（BBS 2.106.2）：游戏签到 luna 的 web DS 与米游币任务 app DS。 */
    const val SALT_BBS_V206 = "idMMaGYmVgPzh3wxmWudUXKUPGidO7GM"
    const val SALT_BBS_WEB_V206 = "G1ktdwFL4IyGkHuuWSmz0wUe9Db9scyK"

    /** MiyoQian 使用的 BBS 版本号（与上面两个 salt 配对）。 */
    const val BBS_VERSION_V206 = "2.106.2"

    private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** DS1: 无参签名（GET）。 */
    fun ds1(salt: String = SALT_K2): String {
        val t = (System.currentTimeMillis() / 1000).toString()
        val r = randomSample6()
        val c = md5Hex("salt=$salt&t=$t&r=$r".toByteArray(Charsets.UTF_8))
        return "$t,$r,$c"
    }

    /** DS2: 带 body/query 签名（POST JSON）。 */
    fun ds2(body: String, query: String = "", salt: String = SALT_X6): String {
        val t = (System.currentTimeMillis() / 1000).toString()
        val r = (100001 + kotlin.random.Random.nextInt(100000)).toString()
        val c = md5Hex("salt=$salt&t=$t&r=$r&b=$body&q=$query".toByteArray(Charsets.UTF_8))
        return "$t,$r,$c"
    }

    /** random.sample 语义：不重复取样 6 个字符。 */
    private fun randomSample6(): String {
        val pool = ALPHANUM.toMutableList()
        pool.shuffle()
        return pool.take(6).joinToString("")
    }

    fun md5Hex(data: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
}
