package io.flassie.vertx.rsp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.flassie.vertx.rsp.utils.checkMethods
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.reflect.full.createType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

class EventBusProxyInvocationHandler(
    vertx: Vertx,
    private val addr: String,
    clazz: Class<*>,
    private val deliveryOptions: DeliveryOptions = DeliveryOptions()
): InvocationHandler {
    private val eventBus = vertx.eventBus()
    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
        enableDefaultTyping()
    }

    init {
        try {
            eventBus.registerDefaultCodec(ServiceException::class.java, ServiceExceptionMessageCodec())
        } catch (e: IllegalStateException) {}

        clazz.checkMethods()
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val deliveryOptionsWithAction = DeliveryOptions(deliveryOptions).apply {
            addHeader("action", method.name)
        }

        val argsMap = method.kotlinFunction!!.valueParameters.mapIndexed { index, kParameter ->
            kParameter.name to args!![index]
        }.toMap()

        val jsonBody = if(args == null) "" else objectMapper.writeValueAsString(argsMap)

        return when (val returnType = method.kotlinFunction!!.returnType) {
            Unit::class.createType() -> {
                invokeWithoutFutureResult(deliveryOptionsWithAction, jsonBody)
            }

            Future::class.createType(returnType.arguments) -> {
                invokeWithFutureResult(deliveryOptionsWithAction, jsonBody)
            }

            else -> return null
        }
    }

    private fun invokeWithFutureResult(
        deliveryOptions: DeliveryOptions,
        body: String
    ): Future<*> {
        val promise = Promise.promise<Any>()

        runCatching {
            eventBus.request<String>(addr, body, deliveryOptions) { result ->
                if (result.succeeded()) {
                    val value = objectMapper.readValue<HashMap<String, Any?>>(
                        result.result().body(),
                        object : TypeReference<HashMap<String, Any?>>() {}
                    )["result"]

                    promise.complete(value)
                } else if (result.failed()) {
                    promise.fail(result.cause())
                }
            }
        }.onFailure {
            promise.fail(it)
        }

        return promise.future()
    }

    private fun invokeWithoutFutureResult(deliveryOptions: DeliveryOptions, body: String) {
        eventBus.request<String>(addr, body, deliveryOptions) { result ->
            if (result.failed()) {
                throw result.cause()
            }
        }
    }
}
