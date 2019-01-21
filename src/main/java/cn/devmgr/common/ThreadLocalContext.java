package cn.devmgr.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ThreadLocalContext {
	private static Log log = LogFactory.getLog(ThreadLocalContext.class);
	public static final String REQUEST = ThreadLocalContext.class.getName() + ".request";
	public static final String RESPONSE = ThreadLocalContext.class.getName() + ".response";
	
	protected ThreadBindings m_bindings = new ThreadBindings();

	private static ThreadLocalContext instance = new ThreadLocalContext();
	
	protected class ThreadBindings extends ThreadLocal<Map<String, Object>> {
	    private String instanceTime = (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")).format(new Date());
		public Map<String, Object> initialValue() {
			return new HashMap<String, Object>();
		}

		public Map<String, Object> getBindings() {
			return (Map<String, Object>) get();
		}
		
		@Override
		public String toString(){
		     return super.toString() + " instanced at " + instanceTime + "; keys=" + getBindings().keySet().toString();
		}
	}
	
	private ThreadLocalContext(){
		
	}
	public static ThreadLocalContext getInstance(){
		return instance;
	}
	public static void setValue(String name, Object value){
		instance.set(name, value);
	}
	public static Object getValue(String name){
		return instance.get(name);
	}
	
	public static void setRequest(HttpServletRequest request){
		instance.set(REQUEST, request);
	}
	
	public static void setResponse(HttpServletResponse response){
		instance.set(RESPONSE, response);
	}
	/**
	 * 返回当前线程会话的HttpServletRequest类
	 * @return
	 */
	public static HttpServletRequest getRequest(){
		return (HttpServletRequest) instance.get(REQUEST);
	}
	
	/**
	 * 返回当前线程会话的HttpServletResponse类
	 * @return
	 */
	public static HttpServletResponse getResponse(){
		return (HttpServletResponse) instance.get(RESPONSE);
	}

	
	/**
	 * Bind this object under this name with the current thread, or remove if the value is null.
	 * 
	 * @param name The binding name.
	 * @param value The object to bind, or null to unbind this name.
	 */
	public void set(String name, Object value) {
		// find the map that might already exist
		Map<String, Object> bindings = m_bindings.getBindings();
		if (bindings == null) {
			log.info("setInThread: no bindings!");
			return;
		}

		if (value == null) {
			bindings.remove(name);
		}else {
			bindings.put(name, value);
		}
	}

	/**
	 * Remove all objects bound to the current thread.
	 */
	public void clear(){
		Map<?, ?> bindings = m_bindings.getBindings();
		if (bindings == null) {
			if(log.isInfoEnabled()){
				log.info("clear: no bindings!");
			}
			return;
		}
		bindings.clear();	
		m_bindings.remove();		
	}

	/**
	 * Find the named object bound to the current thread.
	 * 
	 * @param name The binding name.
	 * @return The object bound by this name, or null if not found.
	 */
	public Object get(String name) {
		Map<?, ?> bindings = m_bindings.getBindings();
		if (bindings == null) {
			log.info("get: no bindings!");
			return null;
		}

		return bindings.get(name);
	}
}
