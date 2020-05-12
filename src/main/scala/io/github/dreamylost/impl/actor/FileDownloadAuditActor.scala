package io.github.dreamylost.impl.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.Constants._
import io.github.dreamylost.impl.actor.FileDownloadActor.{ Result, ShutdownSystem }

/**
 * 接受下载反馈，记录下载速度
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/12
 */
object FileDownloadAuditActor extends LazyLogging {

  private[this] def printSpeed(fileSize: Long, start: Long, end: Long, prefix: String = "", suffix: String = ""): Unit = {
    val speed = fileSize.asInstanceOf[Double] / (end - start).asInstanceOf[Double]
    val s = speed * 1000 / 1024
    logger.info(s"$prefix: ${s.formatted("%.2f")} kb/s, cost time: ${end - start}ms")
    logger.info(suffix)
  }

  //计算速度和处理结果actor
  def apply(): Behavior[Result] = {
    Behaviors.receive { (_, message) =>
      message match {
        case FileDownloadActor.DownloadDoingResult(actorId, processPercent, _) =>
          logger.info(s"actorId $actorId - $processPercent complete now")
        case FileDownloadActor.DownloadResult(actorId, blockSize, startTime, endTime, msg, _) =>
          printSpeed(blockSize.getOrElse(0), startTime.getOrElse(0), endTime.getOrElse(0), prefix = s"actorId $actorId - speed", suffix = msg.getOrElse(s"actorId $actorId finished"))
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
