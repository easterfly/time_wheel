package com.easterfly.shaka.sdk;


/**
 * 
 * @author easterfly
 *
 */
public class ShakaTask {

	private String className;

	private String method;

	private Object param;

	private String cronExpression;

	private int minute;

	private int second;

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className == null ? null : className.trim();
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method == null ? null : method.trim();
	}

	public Object getParam() {
		return param;
	}

	public void setParam(Object param) {
		this.param = param;
	}

	protected int getMinute() {
		return minute;
	}

	protected void setMinute(int minute) {
		this.minute = minute;
	}

	protected int getSecond() {
		return second;
	}

	protected void setSecond(int second) {
		this.second = second;
	}

	public String getCronExpression() {
		return cronExpression;
	}

	public void setCronExpression(String cronExpression) {
		this.cronExpression = cronExpression;
	}

	@Override
	public String toString() {
		return "ShakaTask [className=" + className + ", method=" + method + ", param=" + param + ", cronExpression="
				+ cronExpression + ", minute=" + minute + ", second=" + second + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ShakaTask other = (ShakaTask) obj;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		return true;
	}
}