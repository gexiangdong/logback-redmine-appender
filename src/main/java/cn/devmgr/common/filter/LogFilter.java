package cn.devmgr.common.filter;

import java.io.*;
import java.util.ArrayList;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import cn.devmgr.common.ThreadLocalContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.devmgr.common.exception.CostTooLongException;

@WebFilter(urlPatterns = "/*")
public class LogFilter implements Filter {
	private final static Log log = LogFactory.getLog(LogFilter.class);
	private FilterConfig filterConfig;

	public static final String REQ_BODY = "req_body";

	public void init(FilterConfig config){
		filterConfig = config;
		if(log.isTraceEnabled()){
			log.trace("init with " + filterConfig.getFilterName());
		}
	}

	/**
	 * 销毁
	 */
	public void destroy() {
		if (log.isTraceEnabled()) {
			log.trace("destroy LogFilter....");
		}
		filterConfig = null;
	}

	/**
	 */
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		if (log.isTraceEnabled()) {
			try {
				log.trace("enter LogFilter filter..."
						+ this.hashCode()
						+ " "
						+ ((HttpServletRequest) request).getRequestURL()
								.toString());

			} catch (Exception e) {
				log.trace("enter LogFilter filter..." + this.hashCode());
			}
		}
		long startTime = System.currentTimeMillis();
		CustomHttpServletRequestWrapper req = new CustomHttpServletRequestWrapper((HttpServletRequest) request);
		ThreadLocalContext.setRequest(req);
		if("application/json".equalsIgnoreCase(req.getContentType())){
			ThreadLocalContext.setValue(REQ_BODY, req.getBody());
		}
		try {
			chain.doFilter(req, response);
		} catch(Throwable t){
		    log.error("发现程序中未捕获到的错误", new RuntimeException(t));
		    throw new RuntimeException(t);
		}finally {
			if (log.isTraceEnabled()) {
				log.trace("try to clear ThreadLocal.");
			}
			if (log.isInfoEnabled()) {
				// 只有开启了信息记录模式，才进行计时告警；可以通过对此类设置log4j配置，提升日志级别关闭告警

				// 计算时间需放在清除ThreadLocal前，否则会导致日志无法记录request
				long cost = System.currentTimeMillis() - startTime;
				String uri;
				String method;
				if (request instanceof HttpServletRequest) {
					uri = ((HttpServletRequest) request).getRequestURI();
					method = ((HttpServletRequest) request).getMethod();
				} else {
					uri = "";
					method = "";
				}

				if (cost >= 5000) {
					// 超过1秒的访问一律记录日志（发到Redmine上）
					log.error("此次访问" + uri + "耗时太久，程序需要优化改进。耗时" + cost
							+ "ms.",
							createLongTimeException("程序耗时太久", method,  uri, true));
				} else if (cost > 15000) {
					// 超过400毫秒的访问一律记录日志（发到Redmine上）
					log.error("此次访问" + uri + "耗时较长，需要优化改进程序。耗时" + cost
							+ "ms.",
							createLongTimeException("程序耗时太久", method,  uri, false));
				}

			}
			ThreadLocalContext.getInstance().clear();
		}
	}

	/**
	 * 
	 * @param message
	 * @param url
	 * @param priority
	 *            传递true时，exception在redmine上是普通优先级；false是低优先级
	 * @return
	 */
	private Exception createLongTimeException(String message, String method, String url,
			boolean priority) {
		RuntimeException e;
		if (priority) {
			e = new RuntimeException(method + " " + url + " " + message);
		} else {
			e = new CostTooLongException(method + " " + url + " " + message);
		}
		String baseUrl;
		if (url.contains("/rest/")) {
			// 防止URL里带有ID等参数，造成同一页面过多redmine issue
			int pos = url.indexOf("/", url.indexOf("/rest/") + 6);
			if (pos > 0) {
				baseUrl = url.substring(0,
						url.indexOf("/", url.indexOf("/rest/") + 6));
			} else {
				baseUrl = url;
			}
		} else {
			baseUrl = url;
		}
		ArrayList<StackTraceElement> list = new ArrayList<StackTraceElement>();
		list.add(new StackTraceElement(baseUrl, "", "", 0));
		StackTraceElement[] stes = e.getStackTrace();
		for (StackTraceElement ste : stes) {
			list.add(ste);
		}
		e.setStackTrace(list.toArray(new StackTraceElement[0]));
		return e;
	}

	/**包装后的req.getInputStream()可多次使用*/
	public class CustomHttpServletRequestWrapper extends HttpServletRequestWrapper {

		private final byte[] body;

		public CustomHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
			super(request);
			body = IOUtils.toByteArray(request.getInputStream());
		}

		public String getBody() throws UnsupportedEncodingException {
		    return new String(body, "UTF-8");
        }

		@Override
		public BufferedReader getReader() throws IOException {
			return new BufferedReader(new InputStreamReader(getInputStream()));
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
			return new ServletInputStream() {

				@Override
				public int read() throws IOException {
					return byteArrayInputStream.read();
				}

				@Override
				public boolean isFinished() {
					return false;
				}

				@Override
				public boolean isReady() {
					return false;
				}

				@Override
				public void setReadListener(ReadListener arg0) {
				}
			};
		}
	}
}
