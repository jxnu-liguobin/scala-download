Scala 多线程断点下载
--

## 使用

启动 FileDownloadMain.scala（thread） 或 FileDownloadActorMain.scala（actor）

超大文件线程数量较多，需要修改系统最大打开文件数量

```bash
ulimit -n 2048
```

修改application.conf
```
download {
    # actor重试次数
    retry-times = 10
    # 缓冲区
    buffer-size = 64
    # HTTP超时
    timeout = "10 s"
    file {
        # 线程数或actor数
        thread-count = 10
        # 文件保存地址
        save-path = "/Users/youName/Downloads/scala-download"
        # 默认带下载文件路径
        urls = ["https://downloads.lightbend.com/scala/2.13.2/scala-2.13.2.deb"]
        # 记录下载状态的临时文件，下载成功时自动删除，下载中断时继续下载
        tmp-suffix = ".tmp"
    }
}
```

目前写入是使用seek，因为写入还是单线程IO所以比较慢。如不需要将下载写入文件，可以考虑引入生产者消费者，通过记录序号，还原数据。