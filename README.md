<div># 分布式时间轮SDK</div><div><br></div><div>SDK使用--整合Spring--接入说明</div><div><br></div><div>1. 引入jar包：</div><div>maven依赖</div><div><br></div><div>&lt;dependency&gt;</div><div>&nbsp; &nbsp; &lt;groupId&gt;com.eastefly&lt;/groupId&gt;</div><div>&nbsp; &nbsp; &lt;artifactId&gt;eastefly-shaka-sdk&lt;/artifactId&gt;</div><div>&nbsp; &nbsp; &lt;version&gt;1.0-SNAPSHOT&lt;/version&gt;</div><div>&lt;/dependency&gt;</div><div><br></div><div><br></div><div>2. 添加redis配置信息和任务列表</div><div>示例</div><div><br></div><div>#redis IP和端口</div><div>redis.ip=xx.xx.xx.xx</div><div>redis.port=6379</div><div>&nbsp;</div><div>#第一个任务</div><div>task1.className=com.eastefly.xml.controller.TimeWheel</div><div>task1.method=walk</div><div>task1.cronExpression=*/5 * * * * ?</div><div>&nbsp;</div><div>#第二个任务</div><div>task2.className=com.eastefly.xml.controller.TimeWheel</div><div>task2.method=say</div><div>task2.cronExpression=*/5 * * * * ?</div><div><br></div><div><br></div><div>3. 任务执行入口</div><div>示例</div><div><br></div><div>@Component("timeWheel")</div><div>public class TimeWheel {</div><div>&nbsp; &nbsp; public void walk() {</div><div>&nbsp; &nbsp; &nbsp; &nbsp; System.out.println("walk 3000 miles");</div><div>&nbsp; &nbsp; }</div><div>&nbsp; &nbsp; public void say() {</div><div>&nbsp; &nbsp; &nbsp; &nbsp; System.out.println("say 200 words");</div><div>&nbsp; &nbsp; }</div><div>&nbsp; &nbsp; &nbsp;</div><div>&nbsp; &nbsp; public void eat() {</div><div>&nbsp; &nbsp; &nbsp; &nbsp; System.out.println("eat 200 bread");</div><div>&nbsp; &nbsp; }</div><div>}</div><div><br></div><div><br></div><div>4. Spring注入redis配置和任务列表：</div><div>示例</div><div><br></div><div>&lt;bean id="task1" class="com.eastefly.shaka.sdk.ShakaTask"&gt;</div><div>&nbsp; &nbsp; &lt;property name="className" value="${task1.className}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="method" value="${task1.method}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="cronExpression" value="${task1.cronExpression}"&gt;&lt;/property&gt;</div><div>&lt;/bean&gt;</div><div>&lt;bean id="task2" class="com.eastefly.shaka.sdk.ShakaTask"&gt;</div><div>&nbsp; &nbsp; &lt;property name="className" value="${task2.className}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="method" value="${task2.method}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="cronExpression" value="${task2.cronExpression}"&gt;&lt;/property&gt;</div><div>&lt;/bean&gt;</div><div>&lt;bean id="shakaWheel" class="com.eastefly.shaka.sdk.ShakaWheel"&gt;</div><div>&nbsp; &nbsp; &lt;property name="redisIp" value="${redis.ip}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="redisPort" value="${redis.port}"&gt;&lt;/property&gt;</div><div>&nbsp; &nbsp; &lt;property name="tasks"&gt;</div><div>&nbsp; &nbsp; &nbsp; &nbsp; &lt;list&gt;</div><div>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &lt;ref bean="task1" /&gt;</div><div>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &lt;ref bean="task2" /&gt;</div><div>&nbsp; &nbsp; &nbsp; &nbsp; &lt;/list&gt;</div><div>&nbsp; &nbsp; &lt;/property&gt;</div><div>&lt;/bean&gt;</div><div><br></div><div><br></div><div>5. 引入时间轮并启动、加载数据：</div><div>示例</div><div><br></div><div>@Autowired</div><div>private ShakaWheel shakaWheel;</div><div>&nbsp;</div><div>@RequestMapping("/timeWheel.do")</div><div>public void timeWheel(HttpServletRequest request) {</div><div>&nbsp; &nbsp; // 初始化、启动时间轮、安装配置文件中的任务列表</div><div>&nbsp; &nbsp; shakaWheel.init();</div><div>&nbsp; &nbsp; &nbsp;</div><div>&nbsp; &nbsp; //动态安装过期事件</div><div>&nbsp; &nbsp; ShakaTask shakaTask = new ShakaTask();</div><div>&nbsp; &nbsp; shakaTask.setClassName("com.eastefly.xml.controller.TimeWheel");</div><div>&nbsp; &nbsp; shakaTask.setMethod("eat");</div><div>&nbsp; &nbsp; shakaTask.setCronExpression("*/5 * * * * ?");</div><div>&nbsp; &nbsp; shakaWheel.add(shakaTask);</div><div>}</div>