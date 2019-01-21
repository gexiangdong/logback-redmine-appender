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

### 在 spring boot REST 中有些异常被spring捕获到的问题

RESTController （或其他）中的异常，会被spring boot捕获且把错误信息反馈给客户端，这些异常由于没有log.error记录，而无法在服务端记录。
可以通过写一个带 `@RestControllerAdvice` 注解的类来处理，并用logger.error记录异常。

```java
package cn.devmgr.tutorial;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
 
 
/**
 * 
 * RestController执行过程中发生异常会被此处捕获处理
 *
 */
@RestControllerAdvice
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {
    private final static Logger logger = LoggerFactory.getLogger(ControllerExceptionHandler.class);
     
    /**
     * 通过ExceptionHandler来设置待捕获的异常，Throwable可捕获任何异常，但优先级最低，因此
     * HttpRequestMethodNotSupportedException HttpMediaTypeNotSupportedException
     * HttpMediaTypeNotAcceptableException MissingPathVariableException
     * MissingServletRequestParameterException ServletRequestBindingException
     * ConversionNotSupportedException TypeMismatchException
     * HttpMessageNotReadableException HttpMessageNotWritableException
     * MethodArgumentNotValidException MissingServletRequestPartException
     * BindException NoHandlerFoundException AsyncRequestTimeoutException
     * 等已经在父类声明捕获的异常不会被此方法处理。
     */
    @ExceptionHandler(Throwable.class)
    @ResponseBody
    ResponseEntity<Object> handleControllerException(Throwable ex, WebRequest request) {
        Map<String,String> responseBody = new HashMap<>();
        // 这里控制返回给客户端的信息
        responseBody.put("message","internal server error. " + ex.getMessage());
         
        Exception e;
        if(ex instanceof Exception) {
            e = (Exception) ex;
        }else {
            e = new Exception(ex);
        }
        return handleExceptionInternal(e, responseBody, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
 
    /**
     * 需要覆盖这个方法，并且在此方法里记录日志；查看ResponseEntityExceptionHandler源码可知，
     * 有些异常被父类捕获，不会进入此类的handleControllerException，因此如果在handleControllerException
     * 记录异常日志，会导致部分异常无日志
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        logger.error("内部错误", ex);
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}

```


## LogFilter

为了使异常中能够记录当前访问的URL等信息，需要启动 LogFilter ，在spring boot程序启动类上增加注解

```xml
@ServletComponentScan(basePackages = {"cn.devmgr.common"})
```

可以启动上述注解，另外上述注解还有一个功能，记录处理时间过长的程序，也是发送 issue 到 redmine。
如需关闭此功能很简单把 cn.devmgr.common.filter.LogFilter 的日志级别设置为WARN或以上，就会关闭此功能。

