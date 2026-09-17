# Cross Shulker Sort 跨潜影盒整理

[Minecraft](https://www.minecraft.net/) **26.2 Fabric** mod: a draggable **Q** button in the inventory screen that sorts
your backpack **and** all plain shulker boxes in one click — **server-side, no click simulation**.

Minecraft **26.2 Fabric** 模组：在背包界面添加一个可拖动的 **Q** 按钮，一键整理背包 **+** 所有普通潜影盒。
整理逻辑在**服务端**直接读写数据完成，不模拟任何点击。

## Versions / 版本分支

| 分支 | MC | 存档 | Java | Item Scroller 跟随 | 状态 |
|---|---|---|---|---|---|
| `main` | 26.2 | 数据组件 | 25 | ✅ 0.32.2 | 全绿（回归19+fuzz5000）+ 实机验证 |
| `mc/1.21.1` | 1.21.1 | 数据组件 | 21 | ✅ 0.24.60 | 编译+无头服冒烟通过 |
| `mc/1.20.1` | 1.20.1 | NBT（`BlockEntityTag`） | 17 | ➖（0.20.0 无排序功能，恒 ID 序） | 编译+无头服冒烟通过 |

说明：Yarn 分支的回归/fuzz 测试无法独立运行（`SimpleRegistry` 跨包访问是自 1.17 起的已知
Yarn 限制，与本 mod 无关），逻辑门统一在 `main` 分支执行——各分支整理核心算法完全一致。

## Features / 功能

- **Q button / Q 按钮**：10×10，位于背包界面；点击整理，**按住 Shift 可拖动**，位置保存在 `config/crossshulkersort.json`。
- **Server-side sort / 服务端整理**：快照 → 规划配额 → 守恒预检 → 写入 → 写后回读校验 → 快照回滚。
- **Item Scroller order / 同序**：盒内排序镜像 Item Scroller 规则（未安装时回退为 ID 序，专用服安全）。
- **Bulk first / 大宗优先**：达到阈值（默认 ≥ 6 组）的物品优先装盒，可配置。
- **Consolidation / 合并**：同类物品尽量收进同一盒（含碎片整理 defrag 与大宗归并）。
- **Sequential fill / 按序装满**：装满 27 格再开下一盒；64 / 16 / 不可堆叠分组隔离（不可堆叠有预留盒）。
- **Top-up / 补满**：用同类余量把半格补到整组，不新增格数。
- **Safety / 安全**：
  - 守恒预检：规划结果与快照逐类比对，赤字直接中止；
  - 写后回读：失配则从快照整体回滚（多次实测零丢失）；
  - 多 pass 收敛（默认 ≤ 3 轮）+ 零进展熔断，不会静默空转。

## Scope / 整理范围（默认）

- 只整理**未染色、未命名、无特殊组件、数量为 1** 的原版普通潜影盒（可在配置中放宽）；
- **27 格单一物品装满**的盒子视为已锁定，直接跳过（防止反复搬动，可配置）；
- 改名即视为手动钉住该盒；盒中盒（潜影盒物品）永不作为货物搬运。

## Config / 配置（ModMenu，需同时安装 Cloth Config；单人即时生效，联机读取服务端文件）

| 选项 | 默认 | 说明 |
|---|---|---|
| `includeDyed` / `includeNamed` / `includeLockedFull` | 全 false | 放宽范围：染色盒 / 命名盒 / 满锁盒 |
| `bulkFirst` / `bulkMinStacks` | true / 6 | 大宗优先及阈值（组数） |
| `homeHealing` | true | 同类碎片保留合并 |
| `overflowEnabled` | true | 主盒装满后向有空位的盒溢出 |
| `defragEnabled` / `defragBulkEnabled` | true / true | 碎片整理 / 大宗归并 |
| `topUpEnabled` | true | 半格补满 |
| `reservationEnabled` | true | 不可堆叠预留盒 |
| `maxRounds` | 3 | 单次 Q 最大 pass 数 |
| `useItemScrollerOrder` | true | 镜像 Item Scroller 排序 |
| `chatReport` | true | 聊天栏报告结果 |
| `debugLog` | true | 在日志输出规划诊断（`[CSSort]`） |
| `buttonX` / `buttonY` | 150 / 4 | Q 按钮位置 |

## Dependencies / 依赖

| Mod | 必需 | 作用 |
|---|---|---|
| Fabric API | ✅ | 基础 |
| Item Scroller + MaLiLib | 可选 | 仅客户端：镜像其排序配置 |
| ModMenu + Cloth Config | 可选 | 配置界面（缺 Cloth 时回退为提示页） |

注意：不要与 Inventory Profiles Next 的整理功能同时使用（会互相打架）。

## Build / 构建

需要 **JDK 25**。

```bash
./gradlew clean build        # 必须 clean；产物在 build/libs/
./gradlew regressionTest     # 19 项回归
./gradlew fuzzTest           # 5000 随机态
./gradlew hashTest           # StackKey 哈希契约
```

Gradle wrapper 已配置国内镜像。构建产物 jar 改名即改版本（`gradle.properties` 中 `mod_version`）。

## Install / 安装

把 `build/libs/crossshulkersort-<版本>+26.2.jar` 放入 `.minecraft/mods`（服务端整理逻辑同样需要装在服务端 / 单人游戏）。

## Log keywords / 日志关键字

在 `logs/latest.log` 中搜索 `CSSort`：`place`（规划落子）、`defrag(-bulk)`（合并成功）、
`defrag skip/why`（合并不动的原因）、`top-up/cap`、`MISMATCH/DEFICIT/POST-APPLY`（中止与回滚）、`提前结束`（熔断）。

## License

MIT — see [LICENSE](LICENSE).
