package com.easterfly.shaka.sdk;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.quartz.CronExpression;
import org.quartz.TriggerUtils;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import redis.clients.jedis.Jedis;

/**
 * 鏃堕棿杞�
 * 
 * @author easterfly
 * @date 2018骞�6鏈�25鏃�---涓婂崍11:33:54
 *
 */
public class ShakaWheel implements ApplicationContextAware {
	private final static Logger logger = LoggerFactory.getLogger(ShakaWheel.class);
	private static Thread workerThread;
	private static Thread expireThread;
	private final static AtomicBoolean shutdown = new AtomicBoolean(false);
	private static int februaryDays;
	private static int hour;
	private static int day;
	private static int month;
	private static Slot[][][] wheel = new Slot[13][32][121];
	private static List<Integer> list = Arrays.asList(1, 3, 5, 7, 8, 10, 12);
	private static List<Integer> integers = Arrays.asList(4, 6, 9, 11);
	private static volatile Map<String, String> currentTickIndex = new ConcurrentHashMap<>();
	private static volatile Map<Integer, String> lockMap = new ConcurrentHashMap<>();
	private static Date date;
	private static ApplicationContext applicationContext;
	private static volatile Map<String, Integer> tickMap = new ConcurrentHashMap<>();
	private static volatile ArrayBlockingQueue<ShakaTask> blockingDeque = new ArrayBlockingQueue<ShakaTask>(100);
	private static volatile Jedis jedis;
	private static volatile String redisIp;
	private static volatile int redisPort;
	private static volatile List<ShakaTask> tasks;

	/**
	 * 鍒濆鍖栨椂闂磋疆锛屽缓绔媟edis杩炴帴锛屾瀯閫犵嚎绋嬶紝鍔犺浇閰嶇疆鏂囦欢涓换鍔″垪琛�
	 */
	public void init() {
		jedis = new Jedis(redisIp, redisPort);
		jedis.connect();
		workerThread = new Thread(new TickWorker(), "Timing-Wheel");
		expireThread = new Thread(new ExpireDataHandler(), "Expire-Task");
		initWheel();
		startThread();
	}

	/**
	 * 鏃堕棿杞垵濮嬪寲锛屾Ы浣嶅垵濮嬪寲
	 */
	private void initWheel() {
		int i = 0;
		int j = 0;
		int m = 0;
		for (;;) {
			if (i == wheel.length - 1) {
				i = 0;
				break;
			}
			for (;;) {
				if (j == wheel[i].length - 1) {
					j = 0;
					i++;
					break;
				}
				for (;; m++) {
					if (m == wheel[i][j].length - 1) {
						m = 0;
						j++;
						break;
					}
					wheel[i][j][m] = new Slot();
				}
			}
		}
		logger.info("init wheel finished");
	}

	/**
	 * 鍚姩鏃堕棿杞紝浠庡綋鍓嶆椂闂村紑濮嬭浆鍔�
	 */
	private void startThread() {
		if (shutdown.get()) {
			throw new IllegalStateException("Cannot be started once stopped");
		}
		if (!workerThread.isAlive()) {
			workerThread.start();
		}
		if (!expireThread.isAlive()) {
			expireThread.start();
		}
	}

	/**
	 * 鍔犺浇閰嶇疆鏂囦欢涓殑浠诲姟鍒楄〃锛屽畨瑁呭埌妲戒綅涓�
	 */
	private void loadTasks() {
		if (tasks != null && tasks.size() > 0) {
			for (ShakaTask task : tasks) {
				add(task);
			}
		}
	}
	
	/**
	 * 閿�姣佹椂闂磋疆
	 * 
	 * @return
	 */
	public boolean stop() {
		jedis.close();
		if (!shutdown.compareAndSet(false, true)) {
			return false;
		}
		boolean interrupted = false;
		while (workerThread.isAlive()) {
			workerThread.interrupt();
			try {
				workerThread.join(100);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		return true;
	}

	/**
	 * 鍒ゆ柇褰撳墠骞翠唤灞炰簬骞冲勾鎴栭棸骞达紝浠庤�岀‘瀹氫簩鏈堜唤澶╂暟锛屽苟纭畾褰撳墠绉掋�佸垎銆佹椂銆佹棩銆佹湀
	 */
	private void setParams() {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		month = calendar.get(Calendar.MONTH) + 1;
		day = calendar.get(Calendar.DAY_OF_MONTH);
		hour = calendar.get(Calendar.HOUR_OF_DAY) * 5;
		tickMap.put(TimeUnit.MINUTE, calendar.get(Calendar.MINUTE) * 2);
		tickMap.put(TimeUnit.SECOND, calendar.get(Calendar.SECOND) * 2);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
		Date date = calendar.getTime();
		int year = Integer.parseInt(sdf.format(date));
		if (year % 4 == 0 && year % 100 != 0 || year % 400 == 0) {
			februaryDays = 29;
		} else {
			februaryDays = 28;
		}
	}

	/**
	 * 瀹夎杩囨湡浜嬩欢
	 * 
	 * @param task
	 */
	public void add(ShakaTask task) {
		synchronized (task) {
			Map<String, Integer> map = getTickPosition(task);
			Slot slot = wheel[map.get(TimeUnit.MONTH)][map.get(TimeUnit.DAY)][map.get(TimeUnit.HOUR)];
			slot.add(task);
		}
	}

	/**
	 * 鏍规嵁鎵ц鏃堕棿鑾峰彇妲戒綅鍦板潃
	 * 
	 * @param time
	 * @return
	 */
	private Map<String, Integer> getTickPosition(ShakaTask shakaTask) {
		Date date = new Date();
		try {
			date = getTimeFromCronExpression(shakaTask.getCronExpression());
			logger.info("next time: " + date);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		Map<String, Integer> map = new HashMap<>();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		map.put(TimeUnit.MONTH, cal.get(Calendar.MONTH) + 1);
		map.put(TimeUnit.DAY, cal.get(Calendar.DAY_OF_MONTH));
		map.put(TimeUnit.HOUR, cal.get(Calendar.HOUR_OF_DAY) * 5);
		shakaTask.setMinute(cal.get(Calendar.MINUTE) * 2);
		shakaTask.setSecond(cal.get(Calendar.SECOND) * 2);
		return map;
	}

	/**
	 * 鏍规嵁cron琛ㄨ揪寮忚幏鍙栦笅娆℃墽琛屾椂闂�
	 * @param expression
	 * @return
	 * @throws ParseException
	 */
	private Date getTimeFromCronExpression(String expression) throws ParseException {
		CronExpression cronExpression = new CronExpression(expression);
		return cronExpression.getNextValidTimeAfter(new Date());
	}

	/**
	 * 閫氳繃鍙嶅皠璋冪敤浠诲姟鎵ц鏂规硶
	 * 
	 * @param task
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void expiredTask(ShakaTask task) throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, NoSuchMethodException, SecurityException, IllegalArgumentException,
			InvocationTargetException {
		String className = task.getClassName();
		String methodName = task.getMethod();
		Class clz = Class.forName(className);
		Object obj;
		if (applicationContext != null) {
			obj = applicationContext.getBean(clz);
		} else {
			obj = clz.newInstance();
		}
		Object param = task.getParam();
		if (param == null) {
			Method m = obj.getClass().getDeclaredMethod(methodName);
			m.invoke(obj);
		} else if (param instanceof String) {
			Method m = obj.getClass().getDeclaredMethod(methodName, String.class);
			m.invoke(obj, param);
		} else if (param instanceof Integer) {
			Method m = obj.getClass().getDeclaredMethod(methodName, Integer.class);
			m.invoke(obj, param);
		}
	}

	/**
	 * 鏃堕棿杞嚎绋�
	 * 
	 * @author easterfly
	 *
	 */
	private class TickWorker implements Runnable {

		private long startTime;

		@Override
		public void run() {
			date = new Date();
			setParams();
			loadTasks();
			int i = month;
			int j = day;
			int m = hour;
			int n = tickMap.get(TimeUnit.MINUTE);
			int p = tickMap.get(TimeUnit.SECOND);
			for (; !shutdown.get();) {
				if (i == wheel.length - 1) {
					i = 0;
					setParams();
				}
				currentTickIndex.put(TimeUnit.MONTH, String.format("%02d", i));
				for (; !shutdown.get();) {
					if (j == wheel[i].length - 1 && list.contains(i)) {
						j = 0;
						i++;
						break;
					}
					if (j == wheel[i].length - 2 && integers.contains(i)) {
						j = 0;
						i++;
						break;
					}
					if (j == februaryDays && i == 2) {
						j = 0;
						i++;
						break;
					}
					currentTickIndex.put(TimeUnit.DAY, String.format("%02d", j));
					for (; !shutdown.get();) {
						if (m >= wheel[i][j].length - 1) {
							m = 0;
							j++;
							break;
						}
						currentTickIndex.put(TimeUnit.HOUR, String.format("%02d", m));
						for (; !shutdown.get();) {
							if (n >= 120) {
								n = 0;
								m += 5;
								break;
							}
							currentTickIndex.put(TimeUnit.MINUTE, String.format("%02d", n));
							for (; !shutdown.get(); p += 2) {
								startTime = System.currentTimeMillis();
								if (p >= 120) {
									p = 0;
									n += 2;
									break;
								}
								logger.info(i + "_" + j + "_" + m / 5 + "_" + n / 2 + "_" + p / 2);
								currentTickIndex.put(TimeUnit.SECOND, String.format("%02d", p));
								notifyExpired(currentTickIndex);
								waitForNextTick();
							}
						}
					}
				}
			}
		}

		/**
		 * 澶勭悊浜嬩欢杩囨湡
		 * 
		 * @param idx
		 */
		private void notifyExpired(Map<String, String> map) {
			Slot slot = wheel[Integer.parseInt(map.get(TimeUnit.MONTH))][Integer.parseInt(map.get(TimeUnit.DAY))][Integer
					.parseInt(map.get(TimeUnit.HOUR))];
			int minute = Integer.parseInt(map.get(TimeUnit.MINUTE));
			int second = Integer.parseInt(map.get(TimeUnit.SECOND));
			CopyOnWriteArrayList<ShakaTask> list = slot.getTasks();
			Iterator<ShakaTask> iterator = list.iterator();
			while (iterator.hasNext()) {
				ShakaTask task = iterator.next();
				if (task.getMinute() == minute && task.getSecond() == second) {
					list.remove(task);
					try {
						blockingDeque.put(task);
					} catch (Exception e) {
						e.printStackTrace();
					}
					add(task);
				}
			}
		}

		/**
		 * 妯℃嫙绉掗拡杞姩
		 */
		private void waitForNextTick() {
			for (;;) {
				long currentTime = System.currentTimeMillis();
				long sleepTime = 1000 - (currentTime - startTime);
				if (sleepTime <= 0) {
					break;
				}
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	/**
	 * 澶勭悊杩囨湡浜嬩欢绾跨▼锛屼粠闃诲闃熷垪涓幏鍙栨秷璐�
	 * @author easterfly
	 *
	 */
	private class ExpireDataHandler implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					ShakaTask task = blockingDeque.take();
					lockMap.put(task.hashCode(), UUID.randomUUID().toString());
					boolean result = RedisTool.tryGetDistributedLock(jedis, task.hashCode() + "",
							lockMap.get(task.hashCode()), getInterval(task.getCronExpression()));
					logger.info("result: " + result);
					if (result) {
						expiredTask(task);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		/**
		 * 鏍规嵁cron琛ㄨ揪寮忚幏鍙栦换鍔℃椂闂撮棿闅�
		 * @param cronExpression
		 * @return
		 * @throws ParseException
		 */
		private int getInterval(String cronExpression) throws ParseException {
			CronTriggerImpl cronTriggerImpl = new CronTriggerImpl();
			cronTriggerImpl.setCronExpression(cronExpression);
			List<Date> dates = TriggerUtils.computeFireTimes(cronTriggerImpl, null, 2);
			return (int) (Math.abs(dates.get(0).getTime() - dates.get(1).getTime()) - 100);
		}
	}

	/**
	 * 妲戒綅
	 * 
	 * @author easterfly
	 *
	 */
	private class Slot {

		/**
		 * 褰撳墠妲戒綅鎸傝浇鐨勪换鍔″垪琛�
		 */

		private CopyOnWriteArrayList<ShakaTask> tasks = new CopyOnWriteArrayList<>();

		public void add(ShakaTask task) {
			tasks.add(task);
		}

		public CopyOnWriteArrayList<ShakaTask> getTasks() {
			return tasks;
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		applicationContext = context;
	}

	public void setRedisIp(String ip) {
		redisIp = ip;
	}

	public void setRedisPort(int port) {
		redisPort = port;
	}

	public static void setTasks(List<ShakaTask> shakaTasks) {
		tasks = shakaTasks;
	}
}
