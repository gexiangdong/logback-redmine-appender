# logback 的一个 redmine appender 

当有error发生时，此appender会把异常信息，以及异常发生时的 context 环境信息作为一个 issue 发送到redmine上。


## 使用

### 编译成jar包，并放到项目中

```bash
mvn package
```
把编译好的jar拷贝到项目中，然后按照下面的方法配置logback

### 配置 redmine

在redmine里建立一个自定义字段，名字为issuemd5，字符串型（用于发行issue时，决定是建立一个新的 issue，还是在老的issue上更新）。


建立的规则是：如果同一处异常（根据异常堆栈），而且redmine上还有未关闭的issue，则在此issue下补充一条异常信息。否则创建一个新issue .


### 配置 logback
logback-spring.xml中可这样配置此appernder

```xml

    <appender name="redmine" class="cn.devmgr.common.logback.RedmineIssueAppender">
        <url>https://devmgr.cn/redmine/</url>
        <apiKey>957f727df4eeb33706acb7ba93bea31f734bfcfb</apiKey>
        <projectKey>sample-project</projectKey>
        <issueMD5CustomerFieldId>1</issueMD5CustomerFieldId>
        <issueMD5CustomerFieldName>issuemd5</issueMD5CustomerFieldName>
        <subjectPrefix>每个issue标题的前缀</subjectPrefix>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
    </appender>

```


## LogFilter

为了使异常中能够记录当前访问的URL等信息，需要启动 LogFilter ，在spring boot程序启动类上增加注解

```xml
@ServletComponentScan(basePackages = {"cn.devmgr.common"})
```

可以启动上述注解，另外上述注解还有一个功能，记录处理时间过长的程序，也是发送 issue 到 redmine。
如需关闭此功能很简单把 cn.devmgr.common.filter.LogFilter 的日志级别设置为WARN或以上，就会关闭此功能。

