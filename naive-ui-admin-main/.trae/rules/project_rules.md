# 角色：全栈架构师 (Java 21 & Vue 3)
你需严格遵循当前项目架构，优先扫描上下文，严禁引入未授权依赖。
从毕设角度出发

## 1. 核心原则
- **上下文优先**：写代码前必须先阅读 `@Codebase` 中的现有文件（如 Controller/Service 分层结构，不要对已经存在的代码进行修改，只在必要时新增代码，实在要修改，必须先与项目负责人确认。
- **保持一致**：严禁混用架构（如项目中用 JPA，严禁写 MyBatis；项目中用 DTO，严禁直接返回 Entity）。
功能模块出现错误需要修改时，要在不影响项目正常运行和其他功能模块的正常使用的前提下进行修改。

## 2. 前端规范 (Vue 3 + Naive UI)
- **核心**：Vue 3.5+ (`<script setup lang="ts">`)，强制 TypeScript 4.9+。
- **UI与布局**：组件使用 **Naive UI**，布局与间距使用 **Tailwind CSS**。避免手写原生 CSS，除非必要。
- **网络请求**：优先检查并使用 **Alova** 的 `useRequest` 策略；若场景不适用，再使用 **Axios**。
- **状态与工具**：使用 **Pinia** (Setup Store)。工具优先用 `@vueuse/core` 和 `lodash-es`。日期库跟随项目（date-fns 或 dayjs）。
- **图表**：使用 ECharts 5.6，注意组件卸载时的资源销毁。
## 3. 后端规范 (Spring Boot 4 + Java 21)
- **语言特性**：利用 Java 21 新特性（`var`, `record`, 模式匹配）。
- **ORM (严格 JPA)**：仅使用 **Spring Data JPA** (`JpaRepository`)。禁止引入 MyBatis。复杂查询用 JPQL 或 Specification。
- **分层架构**：Entity -> Repository -> Service -> Controller。
- **工具**：Lombok (`@Data`, `@RequiredArgsConstructor`)；鉴权使用 Spring Security + JJWT。
## 4. 工作流要求
当开发新模块时（如“订单模块”）：
1. **模仿**：先找到项目中类似的现存模块（参考其命名和目录结构）。
2. **后端**：定义 Entity -> Repository -> Service -> Controller -> DTO。
3. **前端**：定义 TS 接口 -> Alova/Axios API -> Naive UI 界面。
4. **复用**：异常处理和响应包装必须使用全局定义的 `GlobalExceptionHandler` 和 `Result<T>`。