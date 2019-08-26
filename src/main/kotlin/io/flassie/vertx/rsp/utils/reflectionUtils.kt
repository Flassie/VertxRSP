package io.flassie.vertx.rsp.utils

import io.flassie.vertx.rsp.exceptions.UnsupportedTypeException
import io.vertx.core.Future
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

internal fun KFunction<*>.isReturnTypeEquals(other: KClass<*>): Boolean {
    return runCatching {
        return returnType == other.createType(returnType.arguments)
    }.getOrNull() ?: false
}

internal fun KFunction<*>.isReturnTypeAnyOf(vararg others: KClass<*>): Boolean {
    for(other in others) {
        if(isReturnTypeEquals(other))
            return true
    }

    return false
}

internal fun KFunction<*>.hasGenericParameters(): Boolean {
    return valueParameters.any {
        it.type.arguments.any { t -> t.type!!.classifier in typeParameters }
    }
}

internal fun Class<*>.checkMethods() {
    declaredMethods
        .map { it.kotlinFunction!! }
        .forEach { method ->
            if(!method.isReturnTypeAnyOf(Unit::class, Future::class))
                throw UnsupportedTypeException(this, method.javaMethod)

            if(method.hasGenericParameters())
                throw UnsupportedTypeException(this, method.javaMethod)
        }
}
