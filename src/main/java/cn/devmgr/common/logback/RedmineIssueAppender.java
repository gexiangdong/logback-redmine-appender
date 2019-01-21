package cn.devmgr.common.logback;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.function.Function;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import cn.devmgr.common.ThreadLocalManager;
import cn.devmgr.common.filter.LogFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.CustomFieldFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueFactory;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TrackerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

public class RedmineIssueAppender extends AppenderBase<ILoggingEvent> {
    private Log log = LogFactory.getLog(RedmineIssueAppender.class);

    private String url;

    private String apiKey;
    private String projectKey;
    private int issueMD5CustomerFieldId = 1;
    private String issueMD5CustomerFieldName = "issuemd5";
    private String subjectPrefix = "";

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().levelInt != Level.ERROR_INT) {
            // 只处理错误，不处理其他
            log.info("不是ERROR，不处理");
            return;
        }

        Integer priority, trackerId;
        // normal priority
        priority = new Integer(2);
        trackerId = null;

        IThrowableProxy tp = event.getThrowableProxy();
        if (tp != null) {
            String ecn = tp.getClassName();
            if (ecn.endsWith("ClientAbortException")) {
                log.info("ClientAbortException，不处理");
                return;
            }
            if (ecn.endsWith("CostTooLongException")) {
                // CostTooLongException记录为低优先级bug
                priority = new Integer(1);
                trackerId = new Integer(3); // Support
            }
            String msg = tp.getMessage();
            if (msg != null) {
                if (msg.contains("ClientAbortException")) {
                    log.info("ClientAbortException，不处理");
                    return;
                }
                if (msg.contains("Closing") && msg.contains("SockJsSession")) {
                    log.info("Closing SockJsSession，不处理");
                    return;
                }
            }
        }

        String issueMd5 = null;
        StringBuffer buf = new StringBuffer();
        buf.append(projectKey);
        if (tp != null) {
            buf.append(tp.getClassName() + ": " + tp.getMessage());
        }
        StackTraceElement[] stes = event.getCallerData();
        int stackCount = 0;
        for (StackTraceElement ste : stes) {

            buf.append(ste.getClassName());
            buf.append(ste.getFileName());
            buf.append(ste.getMethodName());
            buf.append(ste.getLineNumber());
            if (stackCount++ > 30) {
                // 超过30个的堆栈不再继续追溯
                if (log.isInfoEnabled()) {
                    log.info("堆栈过深" + stes.length + ";放弃后面部分");
                    break;
                }
            }
        }
        issueMd5 = md5String(buf.toString());

        StringBuffer error = new StringBuffer();
        error.append(getRequestInformation());

        error.append("\r\n\r\nERROR: (" + event.getLevel() + ") ");
        error.append(event.getMessage() + "; ");
        error.append(event.getFormattedMessage() + "; ");
        error.append("\r\n\r\n\r\n");
        if (tp != null) {
            error.append("    " + tp.getClassName() + "【" + tp.getMessage() + "】\r\n");
        }

        // 堆栈信息
        for (StackTraceElement ste : stes) {
            // 下面留几个空格是为了在redmine中显示成白底引用状态
            //at org.apache.log4j.Category.callAppenders(Category.java:203)

            error.append("    at ");
            error.append(ste.getClassName());
            error.append(".").append(ste.getMethodName());
            error.append("(").append(ste.getFileName()).append(":").append(ste.getLineNumber()).append(")");
            error.append("\r\n");
        }
        error.append("    \r\n    \r\n");

        final String redmineIssueMd5 = issueMd5;
        String subject = subjectPrefix + "-" + event.getMessage();
        if (subject.length() > 200) {
            // 超出255；会创建issue失败；此处只取前200个字符
            subject = subject.substring(0, 200);
        }
        final String redmineIssueSubject = subject;
        final Integer issuePriority = priority;
        final Integer issueTrackerId = trackerId;
        final String redmineIssueNote = error.toString();

        // 创建一个新线程来保存到redmine，以免网络连接速度慢导致干扰主线程的用户响应。
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                RedmineManager mgr = RedmineManagerFactory.createWithApiKey(url, apiKey);
                IssueManager issueManager = mgr.getIssueManager();
                HashMap<String, String> map = new HashMap<String, String>();
                // map.put("created_on", "2015-05-28");
                map.put("status_id", "o");
                map.put("cf_1", redmineIssueMd5);
                map.put("author_id", "me");

                try {
                    List<Issue> issues = issueManager.getIssues(map);
                    if (issues.size() > 0) {
                        // 已经创建过，在此基础上补充
                        for (Issue issue : issues) {
                            issue.setNotes(redmineIssueNote);
                            issueManager.update(issue);
                        }
                    } else {
                        // 创建新issue
                        Project project = mgr.getProjectManager().getProjectByKey(projectKey);
                        Issue issueToCreate = IssueFactory.create(project.getId(), redmineIssueSubject);
                        CustomField cf = CustomFieldFactory.create(issueMD5CustomerFieldId, issueMD5CustomerFieldName,
                                redmineIssueMd5);
                        issueToCreate.addCustomField(cf);
                        issueToCreate.setDescription(redmineIssueNote);
                        if (issueTrackerId != null) {
                            issueToCreate.setTracker(TrackerFactory.create(issueTrackerId, "Support"));
                        }
                        if (issuePriority != null) {
                            issueToCreate.setPriorityId(issuePriority);
                        }
                        issueManager.createIssue(issueToCreate);
                    }
                    if (log.isInfoEnabled()) {
                        log.info("issue " + redmineIssueSubject + " 创建成功。");
                    }
                } catch (RedmineException re) {
                    // 为防止进入死循环，此处不能用log4j记录日志
                    re.printStackTrace();
                    if (log.isInfoEnabled()) {
                        log.info("创建issue " + redmineIssueSubject + " 失败。(" + url + ")" + re);
                    }
                }                
            }

        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    protected String getRequestInformation() {
        StringBuffer buf = new StringBuffer();

        try {

            buf.append("Date:" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S").format(new Date()));
            buf.append("\r\n");

            final HttpServletRequest request = ThreadLocalManager.getRequest();
            if (request != null) {
                buf.append("\r\nIP: " + request.getRemoteAddr());
                buf.append("\r\nBrowser: " + request.getHeader("User-Agent"));
                buf.append("\r\nURI: " + request.getRequestURI());
                buf.append("\r\nServletPath: " + request.getServletPath());
                buf.append("\r\nMethod: " + request.getMethod());

                buf.append("\r\n\r\nRequest Headers:");
                printKeyValue(buf, request.getHeaderNames(), key -> request.getHeader(key));

                buf.append("\r\nRequest Parameter Values:");
                printKeyValue(buf, request.getParameterNames(), key -> request.getParameter(key));

                String body = (String) ThreadLocalManager.getValue(LogFilter.REQ_BODY);
                if (body != null && body.length() > 0) {
                    buf.append("\r\nRequest body:");
                    HashMap<String, Object> map = new ObjectMapper().readValue(body,
                            new TypeReference<HashMap<String, Object>>() {
                            });
                    printKeyValue(buf, new Vector<>(map.keySet()).elements(), key -> map.get(key));
                }

                buf.append("\r\nRequest Attribute Values:");
                printKeyValue(buf, request.getAttributeNames(), key -> request.getAttribute(key));

                HttpSession session = request.getSession(false);
                if (session != null) {
                    buf.append("\r\nSession Id:" + session.getId());
                    printKeyValue(buf, session.getAttributeNames(), key -> session.getAttribute(key));
                }

                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    buf.append("Cookies:\r\n");
                    for (int i = 0; i < cookies.length; i++) {
                        if (cookies[i] == null) {
                            buf.append("\t#" + i + " is null");
                            continue;
                        }
                        buf.append("\t#" + i + "  name=" + cookies[i].getName() + "; value=" + cookies[i].getValue()
                                + "; maxAge=" + cookies[i].getMaxAge() + "; domain=" + cookies[i].getDomain()
                                + "; path=" + cookies[i].getPath() + "; comment=" + cookies[i].getComment()
                                + "; \r\n\t\t" + cookies[i].toString());
                        buf.append("\r\n");
                    }
                    buf.append("\r\n\r\n");
                }
            }

        } catch (Exception e) {
            // 为了防止error触发发邮件，进入死循环，此处不写log.error，改用打印堆栈
            e.printStackTrace();
            log.warn("ERROR:", e);
        }

        return buf.toString();
    }

    private void printKeyValue(StringBuffer buf, Enumeration<String> enums, Function<String, Object> f) {
        while (enums.hasMoreElements()) {
            String key = enums.nextElement();
            buf.append("\r\n  ");
            buf.append(key);
            buf.append(": ");
            buf.append(f.apply(key));
        }
        buf.append("\r\n\r\n");
    }

    private String md5String(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes());
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) {
            throw new RuntimeException("MD5加密出现错误", e);
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public int getIssueMD5CustomerFieldId() {
        return issueMD5CustomerFieldId;
    }

    public void setIssueMD5CustomerFieldId(int issueMD5CustomerFieldId) {
        this.issueMD5CustomerFieldId = issueMD5CustomerFieldId;
    }

    public String getIssueMD5CustomerFieldName() {
        return issueMD5CustomerFieldName;
    }

    public void setIssueMD5CustomerFieldName(String issueMD5CustomerFieldName) {
        this.issueMD5CustomerFieldName = issueMD5CustomerFieldName;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

}
