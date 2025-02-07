/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.atlas.guice

import javax.inject.Singleton
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.multibindings.Multibinder
import com.netflix.atlas.akka.AkkaModule
import com.netflix.atlas.lwcapi.StreamSubscriptionManager
import com.netflix.atlas.lwcapi.ExpressionSplitter
import com.netflix.atlas.lwcapi.StartupDelayService
import com.netflix.iep.guice.LifecycleModule
import com.netflix.iep.service.Service
import com.netflix.spectator.api.Registry
import com.typesafe.config.Config

final class LwcApiModule extends AbstractModule {

  override def configure(): Unit = {
    install(new LifecycleModule)
    install(new AkkaModule)

    val serviceBinder = Multibinder.newSetBinder(binder, classOf[Service])
    serviceBinder.addBinding().to(classOf[StartupDelayService])
  }

  @Provides
  @Singleton
  protected def providesExpressionSplitter(config: Config): ExpressionSplitter = {
    new ExpressionSplitter(config)
  }

  @Provides
  @Singleton
  protected def providesSubscriptionManager(registry: Registry): StreamSubscriptionManager = {
    new StreamSubscriptionManager(registry)
  }

  override def equals(obj: Any): Boolean = {
    obj != null && getClass.equals(obj.getClass)
  }

  override def hashCode(): Int = getClass.hashCode()
}
