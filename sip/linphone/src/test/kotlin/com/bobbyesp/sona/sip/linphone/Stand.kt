/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import java.lang.reflect.Proxy

/**
 * A stand-in for one of Linphone's interfaces (`CallParams`, `CallStats`…), answering [values] by
 * their Java getter's name (`isVideoEnabled`, `getVideoDirection`) and zero, `false` or `null` for
 * the rest. Linphone's interfaces are plain Java; only their implementations call native code, so
 * the mappers are tested on the JVM against these.
 */
internal inline fun <reified T : Any> stand(vararg values: Pair<String, Any?>): T {
    val answers = values.toMap()
    val type = T::class.java
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            in answers -> answers[method.name]
            "toString" -> "stand-in ${type.simpleName} $answers"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> zeroOf(method.returnType)
        }
    } as T
}

internal fun zeroOf(type: Class<*>): Any? =
    when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
