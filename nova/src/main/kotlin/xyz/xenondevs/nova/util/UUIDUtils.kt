package xyz.xenondevs.nova.util

import java.util.*

internal fun UUID.salt(salt: String): UUID =
    UUID.nameUUIDFromBytes((this.toString() + salt).toByteArray())