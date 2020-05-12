package io.github.dreamylost.impl.actor

import java.io.RandomAccessFile
import java.net.URL

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.Constants
import io.github.dreamylost.Constants._
import io.github.dreamylost.FileDownload.getDownloadFileName
import io.github.dreamylost.FileUtils.usingIgnore
import io.github.dreamylost.impl.actor.FileDownloadActor.{ DownloadTask, FileTask, ShutdownSystem, StartDownloadTask }

/**
 * 开启下载任务
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/12
 */
object FileDownloadActorMain extends LazyLogging with App {

  //删除临时文件
  clearTempFiles()
  //下载一个
  download(Constants.DEFAULT_FILE_URLS.head)

  //没有lazy会先获得0值
  //https://dreamylost.cn/scala/Scala-%E5%8F%98%E9%87%8F%E5%88%9D%E5%A7%8B%E5%8C%96%E9%A1%BA%E5%BA%8F.html
  private[this] lazy val threadCount: Long = Constants.threadCount
  private[this] var blockSize: Long = _

  lazy val system: ActorSystem[FileTask] = ActorSystem(FileDownloadActorMain(), "file-download-system-actor")

  //主actor
  def apply(): Behavior[FileTask] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case ShutdownSystem(msg) =>
          system.terminate()
          Behaviors.stopped
        case StartDownloadTask(actorId, start, totalSize, startPos, endPos, url) =>
          val actor = context.spawn(FileDownloadActor(), s"download-file-actor-$actorId")
          val replyTo = context.spawn(FileDownloadAuditActor(), s"download-audit-actor-$actorId")
          actor ! DownloadTask(actorId, start, totalSize, startPos, endPos, url, replyTo)
          Behaviors.same

      }

    }

  def download(fileUrl: String): Unit = {
    val url = new URL(fileUrl)
    val connection = url.openConnection().asHttpUrlConnection()
    val fileName = getDownloadFileName(fileUrl)
    if (connection.getResponseCode == 200) {
      val fileLength = connection.getContentLength
      usingIgnore(new RandomAccessFile(fileName, "rwd")) {
        randomAccessFile => randomAccessFile.setLength(fileLength)
      }
      blockSize = fileLength / threadCount
      logger.info(s"file name: $fileName, file length: $fileLength, each block: ${blockSize / 1024 / 1024}MB")
      for (actorId <- 1L to threadCount) {
        val startPos: Long = (actorId - 1) * blockSize
        var endPos: Long = (actorId * blockSize) - 1
        if (threadCount == actorId) {
          endPos = fileLength
        }
        system ! StartDownloadTask(actorId, System.currentTimeMillis(), fileLength, startPos, endPos, fileUrl)
      }
    } else {
      logger.error(s"network error or file not found: ${connection.getResponseCode}")
    }
    connection.closeConnect()
  }

}
