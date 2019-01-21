package cn.devmgr.common.exception;

public class CostTooLongException extends RuntimeException {

	public CostTooLongException() {
		super();
	}

	public CostTooLongException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CostTooLongException(String message, Throwable cause) {
		super(message, cause);
	}

	public CostTooLongException(String message) {
		super(message);
	}

	public CostTooLongException(Throwable cause) {
		super(cause);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 2574953708536811320L;

}
