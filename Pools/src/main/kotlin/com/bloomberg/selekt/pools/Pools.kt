/*
 * Copyright 2021 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.selekt.pools

import java.io.Closeable
import java.util.concurrent.ScheduledExecutorService
import kotlin.jvm.Throws

fun <K : Any, T : IPooledObject<K>> createObjectPool(
    factory: IObjectFactory<T>,
    executor: ScheduledExecutorService,
    configuration: PoolConfiguration
) = SingleObjectPool(
    factory,
    executor,
    configuration.evictionDelayMillis,
    configuration.evictionIntervalMillis
).let {
    when (configuration.maxTotal) {
        1 -> TieredObjectPool(it, it, factory)
        else -> TieredObjectPool(
            it,
            CommonObjectPool(factory, executor, configuration.copy(maxTotal = configuration.maxTotal - 1), it),
            factory
        )
    }
}

interface IObjectPool<K : Any, T : Any> : Closeable {
    fun borrowObject(): T

    fun borrowObject(key: K): T

    fun returnObject(obj: T)
}

interface IObjectFactory<T : Any> : Closeable {
    fun activateObject(obj: T)

    @Throws(Exception::class)
    fun destroyObject(obj: T)

    fun gauge(): FactoryGauge

    @Throws(Exception::class)
    fun makeObject(): T

    @Throws(Exception::class)
    fun makePrimaryObject(): T

    fun passivateObject(obj: T)

    fun validateObject(obj: T): Boolean
}

interface IPooledObject<K> {
    val isPrimary: Boolean

    var tag: Boolean

    fun matches(key: K): Boolean
}
