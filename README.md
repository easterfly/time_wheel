# 分布式时间轮SDK

SDK使用--整合Spring--接入说明

1. 引入jar包：
maven依赖

<dependency>
    <groupId>com.eastefly</groupId>
    <artifactId>eastefly-shaka-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

2. 添加redis配置信息和任务列表
示例

#redis IP和端口
redis.ip=xx.xx.xx.xx
redis.port=6379
 
#第一个任务
task1.className=com.eastefly.xml.controller.TimeWheel
task1.method=walk
task1.cronExpression=*/5 * * * * ?
 
#第二个任务
task2.className=com.eastefly.xml.controller.TimeWheel
task2.method=say
task2.cronExpression=*/5 * * * * ?
3. 任务执行入口
示例

@Component("timeWheel")
public class TimeWheel {
    public void walk() {
        System.out.println("walk 3000 miles");
    }
    public void say() {
        System.out.println("say 200 words");
    }
     
    public void eat() {
        System.out.println("eat 200 bread");
    }
}
4. Spring注入redis配置和任务列表：
示例

<bean id="task1" class="com.eastefly.shaka.sdk.ShakaTask">
    <property name="className" value="${task1.className}"></property>
    <property name="method" value="${task1.method}"></property>
    <property name="cronExpression" value="${task1.cronExpression}"></property>
</bean>
<bean id="task2" class="com.eastefly.shaka.sdk.ShakaTask">
    <property name="className" value="${task2.className}"></property>
    <property name="method" value="${task2.method}"></property>
    <property name="cronExpression" value="${task2.cronExpression}"></property>
</bean>
<bean id="shakaWheel" class="com.eastefly.shaka.sdk.ShakaWheel">
    <property name="redisIp" value="${redis.ip}"></property>
    <property name="redisPort" value="${redis.port}"></property>
    <property name="tasks">
        <list>
            <ref bean="task1" />
            <ref bean="task2" />
        </list>
    </property>
</bean>
5. 引入时间轮并启动、加载数据：
示例

@Autowired
private ShakaWheel shakaWheel;
 
@RequestMapping("/timeWheel.do")
public void timeWheel(HttpServletRequest request) {
    // 初始化、启动时间轮、安装配置文件中的任务列表
    shakaWheel.init();
     
    //动态安装过期事件
    ShakaTask shakaTask = new ShakaTask();
    shakaTask.setClassName("com.eastefly.xml.controller.TimeWheel");
    shakaTask.setMethod("eat");
    shakaTask.setCronExpression("*/5 * * * * ?");
    shakaWheel.add(shakaTask);
}