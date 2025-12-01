# Java 实验常见问题库

## Q: 实验中遇到 "Error: Could not find or load main class" 怎么办？
A: 这个问题通常是因为编译路径错误或包名不匹配导致的。
1. 检查你的源码文件中是否有 `package` 声明，如果有，运行时需要在包的上一级目录运行 `java com.example.Main`。
2. 检查 `CLASSPATH` 环境变量配置是否正确。
3. 如果使用 IDEA，尝试点击 Build -> Rebuild Project 重新构建项目。

---

## Q: 在 Spring Boot 实验中，@Autowired 注入为 null 是什么原因？
A: 依赖注入失败通常有以下几个原因：
1. 该类没有加注解（如 @Component, @Service, @Controller），导致没被 Spring 容器管理。
2. 你是使用 `new` 关键字手动创建了对象，而不是通过 Spring 容器获取的。
3. 包扫描路径配置错误，Spring 没有扫描到该类所在的包。

---

## Q: 实验要求连接 MySQL，但是报 "Communications link failure" 错误？
A: 这表示 Java 程序无法连接到数据库服务。请检查：
1. 数据库服务是否已启动（Windows 下检查服务，Linux 下使用 `systemctl status mysql`）。
2. `application.properties` 中的 `spring.datasource.url` 端口号（默认3306）是否正确。
3. 数据库用户名和密码是否正确。
4. 防火墙是否拦截了连接。

---

## Q: List 和 Set 在实验数据存储中有什么区别？
A: List 是有序集合，允许元素重复，适合需要保留插入顺序的场景；Set 是无序集合，不允许元素重复，适合去重场景。在实验中，如果要求存储不重复的学生学号，应使用 HashSet；如果要求按录入顺序打印成绩，应使用 ArrayList。

---

## Q: Java 中 == 和 equals() 有什么区别？
A: 在实验中经常会遇到字符串比较的问题：
1. `==` 比较的是两个对象的内存地址（引用是否相同）。
2. `equals()` 比较的是两个对象的内容是否相等。
3. 对于 String 类型，应该使用 `equals()` 方法进行内容比较，而不是使用 `==`。

---

## Q: 在多线程实验中，为什么变量值不同步？
A: 多线程访问共享变量时可能出现数据不一致：
1. 需要使用 `synchronized` 关键字或 `Lock` 来保证线程安全。
2. 对于简单的计数器，可以使用 `AtomicInteger` 等原子类。
3. 使用 `volatile` 关键字保证变量的可见性（但不保证原子性）。

---

## Q: 异常处理中 try-catch-finally 的执行顺序是什么？
A: 在实验中编写异常处理代码时需要注意：
1. `try` 块中的代码正常执行，如果抛出异常则跳转到对应的 `catch` 块。
2. `catch` 块用于捕获并处理特定类型的异常。
3. `finally` 块无论是否发生异常都会执行，通常用于资源清理（如关闭文件、数据库连接）。
4. 如果 `finally` 中有 `return` 语句，会覆盖 `try` 或 `catch` 中的返回值。

---

## Q: Maven 项目编译时报 "程序包不存在" 错误怎么办？
A: 这通常是依赖问题导致的：
1. 检查 `pom.xml` 中是否正确添加了所需的依赖。
2. 尝试在 IDEA 中右键项目，选择 Maven -> Reload Project 刷新依赖。
3. 删除本地仓库中的对应依赖文件夹（位于 `~/.m2/repository`），然后重新下载。
4. 检查网络连接，确保能够访问 Maven 中央仓库。

---

## Q: 如何在实验中正确使用 static 关键字？
A: `static` 关键字的使用场景：
1. `static` 变量属于类而不是对象，所有实例共享同一个静态变量。
2. `static` 方法可以直接通过类名调用，不需要创建对象。
3. `static` 方法中不能直接访问非静态成员变量和方法。
4. 工具类的方法通常声明为 `static`，如 `Math.sqrt()`。

---

## Q: 实验中如何读取配置文件？
A: 在 Java 实验中读取配置文件的常用方法：
1. 使用 `Properties` 类读取 `.properties` 文件。
2. 在 Spring Boot 中，使用 `@Value` 注解或 `@ConfigurationProperties` 读取 `application.properties`。
3. 配置文件通常放在 `src/main/resources` 目录下。
4. 使用 `ClassLoader.getResourceAsStream()` 获取配置文件输入流。
