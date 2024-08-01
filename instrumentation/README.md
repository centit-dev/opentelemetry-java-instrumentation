# 添加一个“插件”
Java 7 版本的 agent 没有插件概念，但我们可以按照插件的理念和 instrumentation 的约定为已有的 instrumentation 添加额外的功能。

## 如何添加一个插件
1. 在 extensions 目录下添加一个新目录，目录名称为插件对应的库的名称。
    1. 比如目标库为 tomcat ，计划支持的最低版本为 8.0 ，那么目录名称为 tomcat-8.0 。
1. 在新目录下添加一个 gradle 文件，文件名为<目录名.gradle>。
    1. 比如目标库为 tomcat ，计划支持的最低版本为 8.0 ，那么文件名为 tomcat-8.0.gradle 。
1. 接下来按照 instrumentation 的方式添加代码：
    1. 包名以 `io.opentelemetry.instrumentation.auto.<目标库>` 开头。
    1. `Instrumentation` 扩展 `Instrumenter.Default`
    1. 帮助类通过实现 `Instrumenter.Default#helperClassNames` 声明
