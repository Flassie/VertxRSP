package io.flassie.vertx.rsp.exceptions

import java.lang.reflect.Method

class UnsupportedTypeException(clazz: Class<*>, method: Method?)
    : IllegalStateException("Non-standard types are not supported currently. Class ${clazz.name}, method: ${method?.name}")
