package io.flassie.vertx.rsp

//import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceExceptionMessageCodec
import kotlin.reflect.full.createType
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.jvm.kotlinFunction

class EventBusServiceHandler<out T: Any>(
    vertx: Vertx,
    private val service: T,
    serviceClass: Class<T>
) : Handler<Message<String>> {
    private val serviceKClass = serviceClass.kotlin

    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
        enableDefaultTyping()
    }

    init {
        try {
            vertx.eventBus().registerDefaultCodec(
                ServiceException::class.java, ServiceExceptionMessageCodec()
            )
        } catch (e: IllegalStateException) {}
    }

    override fun handle(msg: Message<String>) {
        runCatching {
            val body = msg.body()
            val methodName = msg.headers()["action"] ?: throw IllegalStateException("action is not specified")

            val possibleMethods = serviceKClass.java.declaredMethods
                .filter { it.name == methodName }
                .map { it.kotlinFunction!! }

            if(possibleMethods.isEmpty())
                throw IllegalStateException("Method $methodName not found")

            val argsHashMap = if(body.isEmpty() || body.isBlank())
                emptyMap<String, Any?>()
            else objectMapper.readValue<HashMap<String, Any?>>(
                body,
                object : TypeReference<HashMap<String, Any?>>() {}
            )

            val method = possibleMethods.find { func ->
                val keys = argsHashMap.keys
                keys.all { key -> func.findParameterByName(key) != null }
            } ?: throw IllegalStateException("Method $methodName with valid signature not found")

            val paramToValue = argsHashMap.map { entry ->
                method.findParameterByName(entry.key)!! to entry.value
            }.toMap().toMutableMap()
            paramToValue[method.parameters.first()] = service as Any

            if(method.returnType != Unit::class.createType()) {
                val returnValue = method.callBy(paramToValue) as Future<*>
                returnValue.setHandler {
                    if(it.failed()) {
                        msg.fail(500, it.cause().message)
                    } else {
                        val result = mapOf<String, Any>("result" to it.result())
                        val reply = objectMapper.writeValueAsString(result)

                        msg.reply(reply)
                    }
                }
            } else {
                method.callBy(paramToValue)
                msg.reply("")
            }
        }.onFailure {
            msg.fail(500, it.message)
            throw it
        }
    }
}
