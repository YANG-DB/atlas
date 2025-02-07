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
package com.netflix.atlas.akka

import java.io.StringWriter
import java.util.Properties

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import com.netflix.atlas.akka.CustomDirectives._
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions

/**
  * API to browse the configuration for atlas. The endpoint listens on `/api/v2/config` and
  * returns a dump of all of the properties. A subtree can be selected by providing a dot
  * separated config path, e.g., `/api/v2/config/a.b.c`. A single query param, `format`, is
  * supported and can be used to dump the data as json, hocon, or properties. The default format
  * is json.
  */
class ConfigApi(config: Config, implicit val actorRefFactory: ActorRefFactory) extends WebApi {

  private val formats: Map[String, Config => HttpResponse] = Map(
    "hocon"      -> formatHocon _,
    "json"       -> formatJson _,
    "properties" -> formatProperties _
  )

  def routes: Route = {
    endpointPathPrefix("api" / "v2" / "config") {
      pathEndOrSingleSlash {
        get { ctx =>
          ctx.complete(doGet(ctx, None))
        }
      } ~
      path(Remaining) { path =>
        get { ctx =>
          ctx.complete(doGet(ctx, Some(path)))
        }
      }
    }
  }

  private def doGet(ctx: RequestContext, path: Option[String]): HttpResponse = {
    val query = ctx.request.uri.query(mode = Uri.ParsingMode.Relaxed)
    val format = query.get("format").getOrElse("json")
    if (formats.contains(format)) {
      path match {
        case Some(p) if !config.hasPath(p) =>
          DiagnosticMessage.error(StatusCodes.NotFound, s"no matching path '$p'")
        case Some(p) =>
          formats(format)(getPathValue(config, p))
        case None =>
          formats(format)(config)
      }
    } else {
      val fmtList = formats.keySet.toList.sortWith(_ < _).mkString(", ")
      val msg = s"unknown format '$format', valid formats are: $fmtList"
      DiagnosticMessage.error(StatusCodes.BadRequest, msg)
    }
  }

  private def getPathValue(config: Config, p: String): Config = {
    import scala.jdk.CollectionConverters._
    try config.getConfig(p)
    catch {
      case e: ConfigException.WrongType =>
        ConfigFactory.parseMap(Map("value" -> config.getString(p)).asJava)
    }
  }

  private def formatHocon(config: Config): HttpResponse = {
    val str = config.root.render
    val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, str)
    HttpResponse(status = StatusCodes.OK, entity = entity)
  }

  private def formatJson(config: Config): HttpResponse = {
    val opts =
      ConfigRenderOptions.defaults.setJson(true).setComments(false).setOriginComments(false)
    val str = config.root.render(opts)
    val entity = HttpEntity(MediaTypes.`application/json`, str)
    HttpResponse(status = StatusCodes.OK, entity = entity)
  }

  private def formatProperties(config: Config): HttpResponse = {
    import scala.jdk.CollectionConverters._
    val props = new Properties
    config.entrySet.asScala.foreach { t =>
      props.setProperty(t.getKey, s"${t.getValue.unwrapped}")
    }

    val writer = new StringWriter
    props.store(writer, null)
    val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, writer.toString)
    HttpResponse(status = StatusCodes.OK, entity = entity)
  }
}
