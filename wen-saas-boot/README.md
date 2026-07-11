# Wen SaaS Boot

wen-saas 的后端工程：多租户 SaaS 管理后台基础底座，设计哲学是**克制、通用、易扩展**。底座提供租户/组织/权限/认证、自定义字段、字典、文件、通知、任务中心、操作日志、开放 API 等通用能力，业务功能以独立业务模块的形式按需挂载，由启动器决定每次部署包含哪些模块。

## 工程结构

```
wen-saas-boot/
├── pom.xml                        # 父工程
├── core/                          # 底座聚合
│   ├── core-api/                  # 扩展点接口与模型
│   ├── core-impl/                 # 底座实现
│   └── core-web/                  # 底座 Web 层
├── modules/                       # 业务模块聚合（按需添加）
│   └── module-crm_demo/           # CRM 客户管理 Demo 模块
└── app/                           # 启动器（决定本次部署内容）
    └── src/main/
        ├── java/com/wensaas/app/WenSaasBootApplication.java
        └── resources/application*.yml
```

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 25 LTS（启用虚拟线程） |
| Spring Boot | 4.0.x |
| PostgreSQL | 18.x |
| Redis | 稳定版 |
| RocketMQ | 5.x |
| Quartz（内嵌调度） | Boot BOM 托管 |
| MyBatis-Plus | 稳定版 |

版本号集中在父 pom 的 `<properties>` 管理。构建：`mvn -DskipTests package`，启动：`java -jar app/target/wen-saas-boot-app.jar`，健康检查 `GET /actuator/health`。
