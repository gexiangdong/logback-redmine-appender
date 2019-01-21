# logback 的一个 redmine appender 

当有error发生时，此appender会把异常信息，以及异常发生时的 context 环境信息作为一个 issue 发送到redmine上。


## 使用配置

在redmine里建立一个自定义字段，名字为issuemd5，字符串型（用于发行issue时，决定是建立一个新的 issue，还是在老的issue上更新）。

建立的规则是：如果同一处异常（根据异常堆栈），而且redmine上还有未关闭的issue，则在此issue下补充一条异常信息。否则创建一个新issue .


logback-spring.xml中可这样配置此appernder

```xml

    <appender name="redmine" class="cn.devmgr.common.logback.RedmineIssueAppender">
        <url>https://devmgr.cn/redmine/</url>
        <apiKey>957f727df4eeb33706acb7ba93bea31f734bfcfb</apiKey>
        <projectKey>sample-project</projectKey>
        <issueMD5CustomerFieldId>1</issueMD5CustomerFieldId>
        <issueMD5CustomerFieldName>issuemd5</issueMD5CustomerFieldName>
        <subjectPrefix>测试用</subjectPrefix>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
    </appender>

```


s