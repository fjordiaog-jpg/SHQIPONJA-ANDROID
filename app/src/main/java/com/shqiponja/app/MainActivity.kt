private fun toZegoUserId(firebaseUid: String): String {
    val bytes = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(firebaseUid.toByteArray(Charsets.UTF_8))

    return bytes.take(16).joinToString("") {
        "%02x".format(it.toInt() and 0xff)
    }
}
