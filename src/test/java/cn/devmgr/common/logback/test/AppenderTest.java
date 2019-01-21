package cn.devmgr.common.logback.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppenderTest {
    private static final Logger log = LoggerFactory.getLogger(AppenderTest.class);
    
    public void testLogger() throws Exception{
//        log.error("测试错误日志", new RuntimeException("异常测试"));
//        Thread.sleep(10000);
    }

    public static void main(String[] argvs) throws Exception{
        logSomthing("somthing wrong", new RuntimeException("maybe wrong"));
        System.out.println("\r\n******************************************\r\n");
        logSomthing("WRONG AGAIN.");
    }
    private static void logSomthing(String msg, Throwable t) {
        log.error(msg, t);
    }
    private static void logSomthing(String msg) {
        log.warn(msg);
    }
}
