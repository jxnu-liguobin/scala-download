package io.github.dreamylost


import java.io._

import scala.util.control.Exception.ignoring

/**
 * 文件处理工具
 *
 * @author 梦境迷离
 * @version 1.0, 2019-07-15
 */
object FileUtils {

  /**
   * 必须有close方法才能使用
   */
  type Closable = {
    def close(): Unit
  }

  /** 使用贷出模式的调用方不需要处理关闭资源等操作
   *
   * @param resource 资源
   * @param f        处理函数
   * @tparam R 资源类型
   * @tparam T 返回类型
   * @return 返回T类型
   */
  def usingIgnore[R <: Closable, T](resource: => R)(f: R => T): T = {
    try {
      f(resource)
    } finally {
      ignoring(classOf[Throwable]) apply {
        resource.close()
      }
    }
  }

  /** 文件读取为字节数组并转化为字符
   * 缓冲流
   *
   * @param file    文件对象
   * @param charset 期望编码
   * @return 字符串
   */
  def reader(file: File, charset: String): String = {
    //buffer默认8192
    val array: Array[Byte] = usingIgnore(new BufferedInputStream(new FileInputStream(file))) {
      bf => Stream.continually(bf.read).takeWhile(-1 !=).map(_.toByte).toArray
    }
    new String(array, charset)
  }
}
