package com.yucl.log.handle.async;

public interface AppConf
{
  String zkConnect = "10.62.14.53:2181,10.62.14.62:2181,10.62.14.64:2181";
  String groupId = "log2redis";
  int connectionTimeOut = 100000;
  int reconnectInterval = 10000;
  String redisHost = "10.62.14.40";
  int redisPort = 6379;
}
