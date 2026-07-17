# Wen SaaS

多租户 SaaS 管理后台**基础底座**：提供多租户隔离、认证会话、权限引擎、组织架构、自定义字段、任务中心、消息通知、文件存储、数据导入导出、操作审计等开箱能力，业务方以「业务模块」形式在底座上扩展自己的领域功能。

```text
wen-saas-boot/       后端（Java 25 + Spring Boot 4 + PostgreSQL 18 + Redis + RocketMQ 5）
wen-saas-frontend/   前端（运营后台 / 租户后台 / H5 三端，React + pnpm monorepo）
open-docs/           使用与扩展文档（从这里开始）
```

## 快速开始（非程序员版，适用于所有人）

不会写代码也能完整体验：见 [`open-docs/快速开始(非程序员).md`](<open-docs/快速开始(非程序员).md>)——让你的 AI 助手（QClaw 等）把系统部署到你电脑上，再按一份现成需求书开发一个新业务模块，你只负责提需求和验收。

## 快速开始（程序员）

见 [`open-docs/快速开始(程序员).md`](<open-docs/快速开始(程序员).md>)——用 docker compose 拉起 PG / Redis / RocketMQ，构建并启动后端与前端三端。

## 基于底座开发业务

底座定位是「可扩展的地基」而非「可共建的框架」：欢迎基于它开发你的业务模块，底座本身不接受功能共建。开发指南：

- [架构总览](open-docs/架构总览.md)
- [后端业务模块开发](open-docs/后端开发/)（工程结构与 SPI、REST 约定、多租户、权限菜单、自定义字段、异步任务、导入导出、操作日志、文件与通知）
- [前端模块接入](open-docs/前端开发/)
- [部署](open-docs/部署.md)

## License

[Apache License 2.0](LICENSE)
