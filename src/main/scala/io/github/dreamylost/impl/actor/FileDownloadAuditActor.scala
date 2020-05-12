package io.github.dreamylost.impl.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.Constants._
import io.github.dreamylost.impl.actor.FileDownloadActor.{ DownloadTask, Result, ShutdownSystem }

/**
 * 接受下载反馈，记录下载速度，处理下载失败
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/12
 */
object FileDownloadAuditActor extends LazyLogging {

  private[this] lazy val retryMap = new java.util.HashMap[Long, Int]

  private[this] def printSpeed(fileSize: Long, start: Long, end: Long, prefix: String = "", suffix: String = ""): Unit = {
    try {
      val speed = fileSize.asInstanceOf[Double] / (end - start).asInstanceOf[Double]
      val s = speed * 1000 / 1024
      logger.info(s"$prefix: ${s.formatted("%.2f")} kb/s, cost time: ${end - start}ms")
      logger.info(suffix)
    } catch {
      case exception: Exception =>
        logger.error(exception.getLocalizedMessage)
    }
  }

  //计算速度和处理结果actor
  def apply(): Behavior[Result] = {
    Behaviors.receive { (context, message) =>
      message match {
        case FileDownloadActor.DownloadDoingResult(actorId, processPercent, _) =>
          logger.info(s"actorId $actorId - $processPercent complete now")
        case FileDownloadActor.DownloadResult(actorId, blockSize, startTime, endTime, msg, _) =>
          //简单重试
          printSpeed(blockSize, startTime, endTime, prefix = s"actorId $actorId - speed", suffix = msg.getOrElse(s"actorId $actorId finished"))
        case FileDownloadActor.DownloadErrorResult(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, error, replyTo) =>
          if (retryMap.containsKey(actorId)) {
            var count = retryMap.get(actorId)
            if (count < retryTimes) {
              logger.warn(s"error, retry $count times")
              replyTo ! DownloadTask(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, context.self)
              count += 1
              retryMap.put(actorId, count)
            } else {
              FileDownloadActorMain.system ! ShutdownSystem(Some(s"error, retry more than $retryTimes times: $error"))
            }
          } else {
            retryMap.put(actorId, 1)
            replyTo ! DownloadTask(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, context.self)
          }
        case FileDownloadActor.DownloadDoneResult(taskBeginTime, fileTotalLength, _) =>
          val success =
            """
              |  .-')                                       ('-.    .-')     .-')
              | ( OO ).                                   _(  OO)  ( OO ).  ( OO ).
              |(_)---\_) ,--. ,--.     .-----.   .-----. (,------.(_)---\_)(_)---\_)
              |/    _ |  |  | |  |    '  .--./  '  .--./  |  .---'/    _ | /    _ |
              |\  :` `.  |  | | .-')  |  |('-.  |  |('-.  |  |    \  :` `. \  :` `.
              | '..`''.) |  |_|( OO )/_) |OO  )/_) |OO  )(|  '--.  '..`''.) '..`''.)
              |.-._)   \ |  | | `-' /||  |`-'| ||  |`-'|  |  .--' .-._)   \.-._)   \
              |\       /('  '-'(_.-'(_'  '--'\(_'  '--'\  |  `---.\       /\       /
              | `-----'   `-----'      `-----'   `-----'  `------' `-----'  `-----'
              |""".stripMargin
          printSpeed(fileTotalLength, taskBeginTime, System.currentTimeMillis(), prefix = s"total speed: ", suffix = s"all task finished, download successfully\n $success")
          clearTempFiles()
          FileDownloadActorMain.system ! ShutdownSystem(None)
      }
      Behaviors.same
    }
  }
}
