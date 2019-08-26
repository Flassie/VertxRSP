package io.flassie.vertx.rsp

import io.vertx.core.Vertx
import java.lang.reflect.Proxy

object ProxyService {
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun<T> createVertxProxy(vertx: Vertx, addr: String, serviceClass: Class<T>): T {
        return Proxy.newProxyInstance(
            this::class.java.classLoader,
            arrayOf(serviceClass),
            EventBusProxyInvocationHandler(vertx, addr, serviceClass)
        ) as T
    }

    @JvmStatic
    fun<T: Any> registerService(vertx: Vertx, addr: String, serviceClass: Class<T>, service: T) {
        val handler = EventBusServiceHandler<T>(vertx, service, serviceClass)
        vertx.eventBus().consumer(addr, handler)
    }
}
