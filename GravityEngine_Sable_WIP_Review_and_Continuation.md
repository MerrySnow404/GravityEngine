# GravityEngine WIP：Sable／Create／Aeronautics 兼容审查与续做计划

**审查日期：2026-10-09**  
**审查对象：`dev`，`2edc4269eb7c854e43babc2eb3ed8b94b895d864`（`wip`）**  
**增量基线：`afd62c13519556f30089588807c23f6d4f09cd5a`**  
**建议存放：`docs/SABLE_WIP_REPAIR_PLAN.md`**  
**性质：审查结论与执行计划，不是已经完成修复的验收报告。**

## 0. 结论与执行边界

这份 WIP 不是只有接口脚手架。它已经把组合轨迹、子步轨迹、Create 碰撞发布、稀疏质量点、部分流体／攀爬和上下车检查接入生产代码。但目前不能认定兼容正确、完整，也不能直接升级为完整兼容基线。[S01][S07][S14][S16]

本轮最明确的运行时缺陷是：**质量点缓存假设原生替换方块“先减旧、后加新”，Sable 2.0.6 实际“先加新、后减旧”。当方块局部质心发生变化时，缓存永久进入 incomplete，后续 GE 分布式重力校正停止，直到原生质量 tracker 重建。** 该缓存状态错误已经对当前 CI 成品中的生产类执行探针复现；不是只从注释猜测。[S02–S06]

另一方面，组合运动的数学抽象值得保留。本轮对成品的 400 组固定种子随机组合轨迹进行了导数及包围检查，共执行 132,005 个断言，并检查了端点相同但中途折返的分段平移。抽样检查通过，但不等于证明全部连续轨迹、完整碰撞求解器或真实多人场景正确。

**下一步是纠错、接通边界和补实际运行证据，不是再建立一个通用物理框架。** 保留 Sable／Create 的原生物理、质量和控制器权威；保留 GE 的统一碰撞／支撑求解和操作局部快照；不要新增竞争性的质量、姿态、时间、网络或 readiness 所有者。

本计划分两个 checkpoint，每个允许多个小提交：

| Checkpoint | 范围 | 结束条件 |
| --- | --- | --- |
| **CP-S1：纠错与运行时边界收口** | SB-01 质量缓存；SB-02 重力／升力；SB-03 轨迹时间与生命周期；SB-04 单一碰撞所有权；SB-06 下车候选；SB-09 初步文档纠偏 | 确认缺陷修复；高风险路径被最小场景证实正确或修复；无模组与 Sable-only 的真实运行门禁通过 |
| **CP-S2：完整组合场景与证据** | SB-05 环境行为；SB-07 热路径预算；SB-08 C/S 与三种组合配置；SB-09 最终支持矩阵 | 已承诺功能在实际组合中通过；客户端／多人／性能证据可复核；未支持项仍明确受限，不冒充完整兼容 |

两阶段均不得以降低断言、跳过 Mixin、关闭物理效果、吞异常或扩大无条件原生回退来换取 PASS。

## 1. 基线、方法与证据等级

### 1.1 固定版本

| 对象 | 本轮使用的基线 |
| --- | --- |
| GravityEngine | 上述 WIP SHA；必须按代码快照比较，不借用早期报告替代 |
| 主 target | Minecraft 1.21.1／NeoForge 21.1.256；Java 21 |
| common | Java 17 生产字节码边界 |
| Sable | 2.0.6；官方标签 `mc1.21.1-2.0.6-neoforge`，源码 `ad8b4d377741fbd923f4d863b2cf9e462daff9a6` |
| Create | 配置目标 6.0.10；源码对照 `ac0c444d9828da3453ae8cc65338e8de063286fb`；改 Mixin 前仍须核对实际解析 JAR |
| Aeronautics | 配置目标 Bundled 1.3.2；本轮未独立反编译其发行 JAR，不能认证 balloon 的运行时局部变量／ABI |
| CI | run `37909282095`；主 target job `113750582631`；artifact `11605189345` |

目标版本来自代码和构建配置，不是推荐升级清单。本轮不要求升级依赖。[S30][S34]

### 1.2 本轮真正执行了什么

已读取 WIP 与上游对应源码，核对关键调用顺序；取得准确 SHA 对应的 CI 成品并校验 SHA-256；对若干类执行 `javap`；运行质量更新探针和组合轨迹探针。没有修改远程库，没有启动 Minecraft／Sable／Create／Aeronautics 服务器或客户端，没有重跑整机性能对比。

| 标记 | 含义 | 可以形成的结论 |
| --- | --- | --- |
| **D：确认缺陷／结构事实** | 源码调用链明确，必要时有生产类探针 | 可直接开修复项；仍注明是否复现了游戏最终表现 |
| **R：待验证风险** | 存在具体缺口或反例输入，尚缺完整运行复现 | 先做最小复现；不能把全部猜测强制变成大改 |
| **G：完成度／证据缺口** | 功能仅部分实现，或已有代码没有相应真实运行证据 | 补实现或验证；不能用旧 PASS 关闭 |
| **T：明确取舍** | 有正确的有限适用域，不代表所有输入都支持 | 保留并说明边界，不自动视为 bug |

严重性和证据等级独立：P1 风险并不等于已经确认 P1 bug。以下只有 SB-01 获得本轮生产类失败复现；其他条目按自身证据说明处理。

### 1.3 成品与探针

```text
Artifact ZIP SHA-256:
3039d933bd85a5f1d191f855257cf0b7210cec23a2e7fca84ab05f8aa7e4b1cc

JAR SHA-256:
6c69d2c4f0a7709399de5832a361a6fe629577d9c2bafb5672e2b2542a2a9ad8

成品名称：GravityEngine-neoforge-1.21.1-0.0.1.jar
审查探针 JVM：OpenJDK 21.0.12.1
```

轨迹探针输出：

```text
PASS checks=132005 trajectories=400 seed=426
maxVelocityError=2.015016949016207E-8
maxAccelerationRatio=0.8410600019610645
SAMPLED_ZIGZAG_PASS segments=2 rightKnotVelocity=(-4,0,0)
```

这些是抽样的性质检查，不是 132,005 个游戏场景，也不是形式化证明。`maxAccelerationRatio` 只是样本中有限差分与所声明上界之比，不能据此缩小生产容差。质量探针用一个最小 `BlockPos` 值类型替身隔离加载真实 `SableMassPoints` 字节码；没有运行原生 MassTracker 或 Mixin，原生顺序由上游源码核实。该探针故意断言旧缺陷存在，只用于复现；修复后应改用“合法替换保持完整”的目标不变量，不能把这个负面探针原样作为长期通过门槛。

## 2. 已有设计：保留什么，不能承诺什么

### 2.1 应保留的结构

**一个轨迹接口贯穿碰撞、接触速度和支撑搬运。** `RigidTrajectory`、`ComposedRigidTrajectory`、`SampledRigidTrajectory` 不是为了减少重复几行代码而泛化，而是表达原单刚体端点模型无法覆盖的父子复合运动与中途折返。它们留在内部命名空间，没有直接扩大消费者 API。[S10–S13]

**分段轨迹不再被误当成一次直线平移。** `SampledRigidTrajectory.rotating()` 实际兼任“必须走一般曲线路径”的判定；分段结点由 `subdivisionMultiple()` 约束支撑线性化。此命名有歧义，但本轮不要求为了改名打散全链路。后续维护需明确它不是单纯的角速度非零判定。[S12][S13]

**外部 provider 的完整扫掠发现与原生实体固定半径分开。** WIP 放宽的是能够自行证明完整 swept geometry 的外部路径；原生实体 `validatePublication()` 仍保留声明范围／固定 reach 的限制。这不是简单删除了所有 16-block 安全边界，应保留这一区分。[S36]

**Sable 仍拥有刚体物理。** GE 只读取其质量、质心、惯量、pose 和速度，计算重力校正；不应另存一份刚体角动量或再次积分 pose。稀疏质量点可以是原生 tracker 派生索引，但不能变成第二个质量权威。[S06]

**Create 仍拥有 contraption 控制器及乘客关系。** GE 接收其规定运动，提供角色碰撞及支撑查询；不能为了让碰撞测试容易通过，把真实控制器或原生坐席流程换成 GE 的并行实现。[S14][S27]

### 2.2 当前能力矩阵

| 能力 | 当前代码状态 | 本轮评估 |
| --- | --- | --- |
| 父子刚体 pose／速度组合 | common 已接入 | 抽样数学检查通过；保留 |
| 等间隔子步轨迹与结点处理 | common 已实现，服务端 Sable 已挂钩 | 时间有效性、失效和 C/S 模型仍需验证 |
| Sable 刚体几何、COM rebase、teleport、scale 替换 | 已有新增路径 | 不能继续写“完全未实现”；也不能仅凭路径存在认定完成 |
| Create 受控／定向 contraption 发布 | 已实现初版，含父 Sable 组合 | 缺真实控制器、复杂父子与流式生命周期证据 |
| 稀疏 self／attached mass | 已实现 | SB-01 确认错误；不能冻结正确性声明 |
| 浮空块／气球重力 operand | 已挂钩 | 局部替换不等于整条力计算一致；空间梯度能力不完整 |
| 流体、攀爬、脚手架 | 已有环境快照和原生 operand 适配 | 不是空白，但边界及玩法效果尚需覆盖 |
| 上车／下车 | 原生坐席加 GE clearance 初版 | 候选形状与最终提交需要收口 |
| 三种兼容配置 | `sable`、`create`、`aeronautics` 已声明 | CI 普通 build 不能证明全部运行配置通过 |
| 客户端／双客户端 | 已有 harness | 定点同步与人工 delay 不能替代动态平台移动验收 |
| 双向角色—刚体反作用 | 本轮未建立完成证据 | 不属于“角色能站稳”自动带来的能力；单独立项 |

尤其要纠正文档：当前 `SABLE_COMPAT_CHECKLIST.md` 和 `REFACTOR_STATUS.md` 仍以早期 composed-only checkpoint 描述“未实现”项目，与同一提交的生产代码不一致。旧报告不应被删除造假，应保留为历史并另写本次的真实状态。[S31][S32]

## 3. 修补项总表

| ID | 优先级／证据 | 核心问题 | 默认处置 |
| --- | --- | --- | --- |
| **SB-01** | **P1／D＋失败探针** | 质量替换顺序错误导致派生质量点永久失效 | 立即修复；新增最小长期回归 |
| **SB-02** | P1／D＋R＋G | 重力、浮力、速度预修正及 incomplete 策略没有证明为同一语义 | 先明确完整计算链和适用域，再修 operand／提交边界 |
| **SB-03** | P1／R | 子步证据的时间、失效、绕转与索引覆盖 | 用真实物理步骤验证；缺证据不能伪造连续轨迹 |
| **SB-04** | P1／R | 注册存在不等于实际接管，原生／GE 碰撞可能衔接不完整 | 审计两套回调顺序及拒绝路径，不机械替换一个布尔值 |
| **SB-05** | P2／D＋R＋G | 环境数据的几何／时间、部分遮水、玩法语义 | 补边界；不要重写完整原生 travel |
| **SB-06** | P2／D＋R | 下车候选 AABB 被丢弃，实际验证当前身体 | 保持候选与最终安装一致；先复现姿态差异 |
| **SB-07** | P2／D | Create 全源遍历与预算前全量排序 | 先消除重复／前置无界工作，再测性能 |
| **SB-08** | P1／G＋R | 客户端轨迹证据与真正动态多人场景不足 | 延伸现有 harness，单独声明传输／模拟假设 |
| **SB-09** | P2／D＋G | 状态文档、版本回退、可复核门禁结果不一致 | 修正文档／产物身份；固化现有测试输出 |

本轮没有证明每一条 R 都已产生游戏 bug。执行者对 R 必须给出“复现并修复”或“有证据排除”，不能仅用“正常使用大概不会遇到”关闭。

## 4. 逐项修正要求

### SB-01：质量替换必须服从原生事务顺序

**入口：**`SableMassPoints.add/snapshot`、`SableMassTrackerMixin.gravityengine$massEdited`、上游 `SubLevelPhysicsSystem.updateMassDataFromBlockChange`。[S02–S05]

实际顺序是先加入新状态质量，后减去旧状态质量。当前缓存却在第一次看到同一 cell 的新质心时立即 `incomplete()`；此后 `complete=false` 不会因正常增量编辑恢复。`SableGravityBridge.capture()` 遇到 null snapshot 返回失败，`apply()` 不再提交该刚体的 GE 重力差量。[S06]

最小反例：同一方块从局部质心 `(0.5,0.25,0.5)` 的形状替换为 `(0.5,0.5,0.5)` 的形状。探针用旧质量 1、新质量 2，按原生顺序操作，结果：

```text
UPSTREAM_ADD_NEW_THEN_REMOVE_OLD=null
AFTER_SUBSEQUENT_VALID_EDIT=null
REVERSE_ORDER_CONTROL=[... mass=2.0]
```

这里质量值用于隔离顺序，并非声称所有半砖／完整方块都具有该质量。应在实际游戏测试中使用真实属性与质心。

**修正方向：**优先在原生方块替换的完整边界更新派生 cell 数据，或用能够表达临时正负质量及一阶矩的最小局部模型，直到原生事务结束再形成最终点。必须同时覆盖原生 build、独立合法 add/remove、mass tracker 替换和失败重建。不能让中间数据作为完整样本被重力桥消费。

**禁止：**擅自把原生顺序改为先删除；清空缓存后假称 COMPLETE；每个子步重新扫描世界；让 GE 缓存覆盖原生 mass／COM／inertia；永久失效却没有恢复触发和诊断。

**完成证据：**原生替换路径中的质心变化、相同质心质量变化、正→零、零→正、重建、附属 contraption 同样场景全部符合原生 tracker；替换后的下一合法步骤仍执行 GE 校正；无二次计重。只需扩展既有质量／Sable controls，不为每个变体建立新测试类。

### SB-02：重力与升力必须有完整的操作数及失败契约

**已确认结构事实：**`SableFloatingGravityMixin` 替换 `FloatingBlockController.physicsTick()` 中一次原生重力读取；该读取位置是整艘 SubLevel 的 `logicalPose().position()`，随后同一个 `localGravity` 用于多个 cluster。当前不是逐升力位置采样。[S22][S24]

同时，上游 `ServerSubLevel.prePhysicsTick()` 在调用浮空块控制器前，仍用原生维度重力对传入 linear velocity 做预修正。WIP 新钩子没有覆盖此处。质量重力桥另在 force 阶段进行 GE 差量校正。因此“已修改 getGravity 调用”不能证明整条计算使用一致的重力。[S06][S25]

**待验证风险：**升力可能已消费某个位置的 complete GE 样本，稍后质量点查询出现 incomplete，导致质量校正被拒绝而已提交／排队的升力保持 GE 值。当前没有证明这是一致的退化策略。是否形成可见失衡，需在实际顺序中验证，不应直接回滚所有原生力。

**分两步完成：**

1. 先对均匀自定义重力关闭语义缺口：明确浮空块的速度预修正、方向、阻尼依赖和重力差量顺序；比较零场、默认场、反向场。未确认原生预修正目的之前，不删除它，也不照抄改写整个 `prePhysicsTick`。
2. 再实现／声明空间变化能力：位于不同位置的升力贡献不能无条件共享 COM 单点样本。保留原生 cluster 数据和气球模型，通过有界的空间采样／聚合表达梯度；需要的状态只属于本子步。对采样尺度与梯度分辨率作清晰说明，而不是宣称有限点求和能精确积分任意场。

对同一物理材料点，重力／升力查询应约定一致的位置、速度、参考系和时间。若未来按附属运动部件采样，速度必须含父刚体运动和子运动各一次，不能仅传父刚体线／角速度。当前 `liftGravity` 并没有通用的 child motion 输入；它只能表达现有单点调用所提供的信息。[S06]

**失败策略：**明确哪些 GE 修正必须原子接受，哪些是独立原生行为。至少不能把 incomplete 当零场、默默只应用一半 GE 修正，或撤销此前属于其他模组的力。暂时无法实现一致校正的域必须保持明确限制，并带原因诊断。

**完成证据：**质量力与升力的解析均匀场结果；对称梯度和力矩；后一个查询 incomplete 的全流程；场解除；移动附属部件；气球与浮空块各自的真实钩子。现有“查询调用了／速度变了”断言不能替代方向、量纲和净力矩检查。

### SB-03：子步轨迹必须描述实际捕获的时间，不只是合法数值

**入口：**`SableRigidCollisionProvider.beginMotion/physicsSample/motion/segment`、`SablePhysicsSamplesMixin`、`SablePublicationMixin`。[S07–S09]

挂钩位置是合理的：子步完成后 `updateAllPoses` 才采样。公共分段轨迹也校验结点位置、方向及分段身份。但是平台 Entry 的样本列表本身没有独立记录明确的捕获 tick；构造轨迹时使用传入 query 的 tick。这个做法需要原生顺序保证，不能仅靠最终值看起来合法。[S04][S07][S12]

需要关闭的具体边缘：原生 begin/reset 之前的查询，物理暂停／恢复，tick 中途新建刚体，配置改变子步数，采样期间 COM rebase／teleport／scale 改变，部分样本缺失，删除／重建，客户端只有端点，没有服务器完整样本。

**修正要求：**写清样本属于哪个 interval；只在对应捕获完成、生命周期一致时提供该轨迹。优先复用已有原生 tick/phase、revision、epoch；只有确实无法推导时才增加最小的捕获时间字段，不建立“轨迹会话管理器”。

端点不能确定任意多圈旋转，也不能证明子步内部任意加速度。当前末端角速度与步长检查只是部分保护。定义原生物理插值模型和不支持的输入；不能把超过可证明范围的运动压成短弧并报告完整。反例至少包括相同起终点的中途折返、完整一圈和末端低角速度但中间较快。

**发现与拒绝顺序：**不完整／不支持的相关来源仍须留在保守候选域里，不能因为无法构建轨迹就从索引中消失。若 fallback enclosure 只看端点，它是否覆盖有中途远离的历史，需要单独证明。预算耗尽必须是显式不可确定，不是成功无碰撞。

### SB-04：统一碰撞与搬运所有权，但不要把“注册存在”当“已接管”

**入口：**`CreateContraptionColliderMixin`、`CreateRigidCollisionProvider.publishes`、Sable 原生角色适配、`EntityMovementIntegration`。[S14][S15]

当前 `publishes()` 证明的是该实体在 provider map 中注册，并不证明本次几何已准备、轨迹可用或 GE 捕获完成。原生 collider 的过滤还结合能力判定，但这些仍不能单独证明同一物理周期一定由 GE 成功执行。

这是需要真实调用顺序确认的风险，不等于已经证明丢碰撞。把过滤条件机械改成 `isInMove()` 也不正确：Create 和 GE 可能在不同原生回调阶段工作，必须先理解各自的步序。

**修正要求：**建立最小所有权表，区分 capability、来源发现、操作授权和完成结果。每条路径必须落在 GE-owned、native-owned 或显式阻塞／安全交接之一；注册、移除、异步几何到达、客户端未收到 contraption 数据、捕获失败时不能既关闭原生，又没有合法 GE 结果。合法 GE route 上不能重复碰撞、平台 carry 或释放速度。

原生坐席／车辆控制器仍拥有乘客位置。不要把乘客再当成 GE 自走角色额外搬运；角色落到平台和真正 `startRiding` 是不同状态。GE 拒绝捕获时，不允许在精确身体仍安装的情况下无条件交回原生 AABB。

**完成证据：**默认重力和任意重力；真实运行的 bearing／piston／oriented contraption；进入退出载具；来源未准备和移除；每一步运动和 carry 的贡献各一次。测试可记录边界次数，但还必须比较实际位置／速度／支撑身份。

### SB-05：环境适配必须补齐几何、时间与原生玩法语义

**入口：**`MovementEnvironmentCapture`、Sable／Create `captureEnvironment`、`EntityFluidEnvironmentMixin`、`LivingEntityEnvironmentMovementMixin`。[S07][S14][S16–S18]

当前已经构建流体／攀爬几何快照并保留原生 travel 分支，这是正确方向，不应退回复制整个原生运动方法。需要修补的不是“再加一个 fluid 布尔值”，而是以下边界：

| 边缘 | 当前需要验证／修正的语义 |
| --- | --- |
| 部分遮水 | Sable 当前通过 actor 中心或 anchor 的遮水命中抑制整个父世界流体捕获。必须验证跨船壳／半身浸水，不能用一个点的遮水替代所有重叠体积 |
| 时间对齐 | Sable／Create 环境使用 `poseAt(0)`；说明它对应角色哪个原生阶段，避免角色当前位姿和另一时间的流体混用 |
| 多种流体／重叠来源 | `fluidMaterialVelocity` 是按 region 平均；说明选择与权重，不因细分 cell 数变化引入任意运动差异 |
| 几何覆盖 | 流体表面、eye 判定、梯子脚点、脚手架边缘必须使用实际空间；未知 chunk 不得当空气 |
| 原生效果 | 推流、游泳／跳水、熄火、窒息、伤害、攀爬限制和原生 fluid type 能力由其原所有者处理，不能只修改画面／flag |
| 自定义块 | `isLadder`／`canStandOnFluid`／context-sensitive shape 使用实际 subject 和存储地址；world／plot／contraption-world 不能混用 |
| 失败路径 | 不得通过返回旧的“在水里”结果而无限延长过期物理许可；明确暂时未知时哪些效果维持、哪些不推进 |

同一身体的一次环境操作可以有原生要求的前后采样，不能为追求“只采一次”错误跨阶段复用；但相同查询的重复全捕获应消除。`captureOrCurrent()` 不应变成任意调用点都能重新读取世界的万能逃生入口。

**完成证据：**静态世界默认重力 parity、倾斜／移动的流体体积、局部遮水、梯子和脚手架、进入退出边缘、缺数据和恢复。先完善现有环境／Sable environment controls，避免按每个方块增加一组长期样板。

### SB-06：下车的“候选形状”不能丢失

**确认的操作数问题：**`DismountGeometryMixin.canDismountTo` 把调用者给出的 candidate AABB 缩成一个 anchor，交给 `CharacterPlacementValidation.clearAt()`；后者平移当前 `GravityEntityGeometry.body(actor)`，没有保留候选尺寸，也不使用原调用的 CollisionGetter。[S19][S20]

这不直接证明游戏一定穿透，但能确认：**当前代码没有证明“刚才验证的形状”和“原生随后提交的候选姿态”相同。** 当蹲伏、站立、爬行或尺寸改变时，可能错误接受或错误拒绝候选。若设计只允许当前安装形状，应明确保证后续不会换成另一形状，而不是同时沿用原生候选成功的含义。

`EntityDismountMixin` 还可能在 void 写入边界直接返回；必须检查是否已经发生解除乘坐、临时 pose 修改或其他副作用，确保拒绝不会留下半完成状态。[S21]

**修正要求：**把候选姿态／尺寸与坐标转换传到最小验证边界，或者证明并约束最终安装使用同一已验证身体。恢复原生候选枚举与下一候选逻辑；拒绝未知时不能凭空选一个看起来空的坐标。检查是否需要 pose-fit 的普通实体阻挡语义，不能仅用运动碰撞列表替代一切占位判断。

**完成证据：**矮顶下坐姿→蹲伏／站立、多候选、旋转载具、坐席移除、缺区块、验证失败后乘客关系和最终几何一致。不要为上下车另外建立一套持久 riding 状态机。

### SB-07：先修重复工作与预算前工作，再讨论优化指标

**确认的结构事实：**Create provider 的 `mayAffect()`、`capture()` 和 `captureEnvironment()` 遍历 Level 内来源；相关 contraption 会把全部 block entry 排序成 list，再作后续处理。环境路径先执行全量排序，才逐项调用 `result.visit()` 记预算。[S14]

这会带来与“附近实际有多少碰撞体”不成比例的成本。尤其环境分支的预算不能限制排序和列表分配之前的工作。当前总源数量上限并不等于局部查询成本低，也不能证明多角色／多载具下的 p95 合格。这里是结构风险，尚无本轮整机耗时测量。

**修正顺序：**

1. 复用原生生命周期／bounds 更新，为来源做保守局部发现；未知来源保留可诊断的保守范围，不作为 proven-empty。
2. 静态 block address 顺序可作为几何 revision 派生数据；不要在每次角色查询重排全体方块。最终 collision shape 仍须保留 subject-specific 求值。
3. 在候选枚举、排序／复制、primitive 展开之前计费，或使用有界、可中止的遍历；不要只限制最终输出数量。
4. 同一操作中复用相同 parent／child motion capture；不要把操作内复用扩展为没有 source/time 证明的跨 tick 缓存。
5. 稀疏质量点消除了每个子步的 bounding-volume 扫描，但每点 FIELD 采样和附属质量校验仍有成本；完整功能打开时一起测。

诊断要记录访问来源数、block 候选、shape 调用、排序／分配、样本数、FIELD query 数、预算拒绝及任务年龄。默认禁用详细诊断时不得逐实体输出日志。

**性能门槛：**不预填不真实的百分比。比较无来源、已注册但附近无关、1／多 contraption、稀疏大 bounds、高子步、多角色、全组合场景。报告中位数与尾部、分配和实际工作；修复过去跳过必要计算的错误所增加的成本必须单列，不能算成算法退化后再把正确性删回去。

### SB-08：服务器采样通过不等于客户端看到同一运动

服务器 `physicsSample()` 能得到子步序列；客户端没有同样的服务器物理采样钩子，现有 motion 路径可退回 last/logical 端点。即使各自数学正确，两端仍可能看到不同的中途扫掠、接触速度和支撑搬运。[S07][S08]

两端只有相同起终点，不能证明中间轨迹相同：`0→2→0` 和静止就是反例。这是具体的表示缺口，不是本轮已经复现的联机穿透。现有多人检查确实会延迟入站消息，但整体保持顺序，主要检查位置、重力和 camera 不改物理；这些不是动态 contraption 碰撞证据。[S29]

**先测、再改协议。** 在实际客户端、服务端和两个观察者中记录同一原生来源的 sample time、source identity、continuity、pose、motion domain、contact point／surface velocity。先排除 tick 取错、原生预测读取错、frame 插值混用、支撑重复计入等集成问题。

需要明确各域能提供什么：相同的规定运动、明确的延迟／预测模型，或不支持的高速／非连续运动。若最终必须增加 motion sample 传输，只传无法从原生消息还原的最小不可变证据，并定义有限窗口和丢失／重置规则；不要复制 Sable 全部 pose 网络状态，也不要引入全局 readiness 或无限历史回放。

保留既有混合权威选择：本轮不得为掩盖预测不一致而擅自恢复 GE 包装移动的服务端速度／终点拒绝。真实物理分歧和视觉平滑要分开度量。

**多人矩阵至少增加：**持续走在旋转父体＋运动子体上、静止角色被来袭平台触碰、中途反向、场切换、附属块编辑、坐席上/下、区块替换、重生／跨维度／重连。正常模式与有界延迟都执行；不得只传送到预定位置后等待稳定。

### SB-09：把代码、版本、文档和验证证据重新对齐

现有文档写“adapter 仍不发布 contraption”“尚无 sampled/sparse/environment/profile”，生产代码却已经包含这些路径。当前普通 CI 构建成功，执行了 JUnit、JVM controls、API artifact 检查；没有执行 `runSableVerification`、真实 compatibility client 或多人阶段。[S30–S32][S34][S35]

根 `mod_version` 在本 WIP 变回 `0.0.1`，CI 成品也使用这个版本，而前序文档把已迁移 API 定义为 `0.0.2`。不是说必须立即发布新稳定版本，而是不能用旧版本身份承载不同契约而不解释。采用明确的新开发版本或恢复正确基线，并统一文档、依赖例子与产物身份；不得覆盖既有发行物。[S33]

**修正要求：**保留历史报告，增加当前精确 SHA／工作区差异的状态；每个能力区分“代码有入口”“实际运行过”“受限支持”“未验证”。维护一个当前支持矩阵，不在 README／AGENTS／多个报告复制不同事实。

三种兼容 profile 已经存在，不要重新创建第四套近似配置。使用它们执行；每次隔离或清理旧结果，归档标准输出、结果 manifest、mod/JAR hash、实际 Mixin 状态与源码 SHA。缺失／跳过必需 marker 必须失败；没有真实运行时仍写 BLOCKED/NOT RUN。

新 Mixin 的 optional guard 主要依据类存在，不能独自证明任意版本 ABI 兼容。为实际支持组合检查版本限制、发行 JAR／依赖身份、客户端和 dedicated-server 不存在依赖时的类加载。其他 target build 通过仍不等于已移植。[S37]

## 5. 继续实现的约束与小型契约表

| 事实 | 权威 | GE 可以保存什么 | 失效边界 |
| --- | --- | --- | --- |
| 刚体 mass／COM／inertia／速度／pose | Sable | 本子步不可变捕获；原生 tracker 派生稀疏索引 | 原生编辑事务、tracker 重建、source 卸载 |
| contraption 控制器和坐席 | Create／原生平台 | 合格来源身份、规定轨迹、查询用几何 | 装配、卸载、换父体、几何变化 |
| 查询可用性与 motion interval | 具体来源捕获边界 | 对应 operation 的证据及拒绝原因 | tick/phase 不匹配、teleport、缺样本 |
| GE collider／support | 已提交身体和当前操作 | 精确几何、有限支撑见证、一次性贡献记录 | 几何提交、离开支撑、来源/连续性改变 |
| FIELD coverage | 每个 provider 的该次查询 | 该次结果，不是 Level 状态 | 下一个不同上下文查询 |
| 客户端展示 | 现有客户端 presentation | 有界插值历史 | 对象替换、场景切换、断开 |

公共值需要写明位置单位 blocks、速度 blocks/tick、加速度 blocks/tick²；Sable 力计算所需 seconds、速度／加速度换算在 adapter 边界完成一次。归一化轨迹参数不是秒，不能省略 interval 换算。

不要为共同拥有 `pose`、`revision` 字段就把重力 provider、碰撞 provider、升力、坐席合并成一个巨大泛型系统。它们可以复用数学或已有捕获上下文，但不能合并不同的生命周期和权威。

## 6. 最小长期测试与验收设计

尽量扩展既有 controls 和已有轨迹测试。下面是行为族，不是要求建立同名新类。临时诊断探针可留在 review evidence，不必进入生产测试常驻依赖。

| 行为族 | 需要守住的不变量 | 首选现有位置 |
| --- | --- | --- |
| 质量派生数据 | 合法原生替换不能永久丢失样本；不双计质量；可按原生生命周期恢复 | `SableRuntimeChecks`／attached mass controls |
| 轨迹一致性 | pose 导数与 point velocity 一致；结点保留；扫掠包围含中途运动 | 现有 composed／sampled common 测试 |
| 原生接入与所有权 | 真实控制器驱动，GE／native 对一贡献只执行一次 | `CreateRuntimeChecks`、Sable transport／collision controls |
| 环境与上下车 | 判断体、查询时间与最终安装一致；缺数据不是空气 | environment／seat controls |
| 力提交 | 质量／升力适用域明确；incomplete 不造成未声明的半次 GE 修正 | Sable gravity 与 `AeronauticsRuntimeChecks` |
| 两端与性能 | 多人动态场景收敛可解释；预算覆盖前置工作；无关源成本可见 | 既有 client／multiplayer harness 与局部测量 |

禁止把失败用例改成只检查“没有抛异常”。数值阈值按原物理契约选取；不能通过放松所有容差掩盖坐标或时间错误。确需修正历史断言时，先说明它固定了哪项错误语义，再用正确的不变量替代。

CP-S1 阻断条件：SB-01 未关闭，相关来源因缺数据被漏掉，双重碰撞／carry，失败后半完成几何／质量操作，或 ordinary/Sable-only 新鲜运行结果缺失。

CP-S2 阻断条件：宣称支持的完整组合没有实际 runtime/client 证据，梯度场／附属运动存在未声明近似，动态多人只有静态同步测试，或无关来源使预算前工作失控。未承诺的反作用、任意非刚性变形等可以明确排除，但不能把已经承诺的功能临时标为 unsupported 以伪造完成。

## 7. 执行提示词

以下提示词按顺序交给执行者。每段首先读取本文件、现有 AGENTS 与当前源码；本文件是审查输入，不是实现已完成的证明。任务涉及的 R 项先复现，证据排除后保留解释，不强行引入修补状态。

### 提示词 A：修复质量缓存与原生编辑边界（CP-S1 第一项）

```text
在 Mafuyu404/GravityEngine 的 dev 工作区，基于 SABLE_WIP_REPAIR_PLAN.md 执行 SB-01。
审查基线为 2edc4269eb7c854e43babc2eb3ed8b94b895d864；若 HEAD 已变化，先定位等价代码，保留无关用户改动。

先核查实际 Sable 2.0.6 JAR / 对应源码的 updateMassDataFromBlockChange、MassTracker.build 和 addBlockMass。
确认“先加新、后减旧”及自定义方块质心；用真实原生替换复现 SableMassPoints 在质心变化后永久 incomplete。
修复派生缓存使其服从完整原生事务，不改变原生质量操作顺序、不新增 GE 质量权威、不每 tick 全扫描世界。
处理 build、单独增删、replacement、tracker 重建和附属 contraption；中间证据不能被当作完整结果。
失败应可诊断，并由明确原生事件恢复，不无限维持“失效但没人在意”。

复用现有 Sable/attached-mass controls，保留一个覆盖质心改变、后续合法编辑及恢复的长期不变量。
验证实际净质量、质心和 GE 校正没有丢失或重复，不只检查 snapshot 非 null。
运行相关 common/target/JVM 与 Sable-only 真实门禁；不能运行时写 BLOCKED 和实际原因。
输出改动文件、旧路径反例、新的唯一写入/失效边界、执行命令及原始结果。完成后停止，不扩大到网络或目录重构。
```

### 提示词 B：轨迹时间、保守发现、原生接管和下车（CP-S1）

```text
执行本计划 SB-03、SB-04、SB-06。保留已存在 RigidTrajectory/Composed/Sampled 及统一求解器。

对照原生 tick/子步/更新 pose/实体 collision 顺序，逐一追踪样本产生、完成、消费和失效。
覆盖暂停、首帧、缺样本、子步数量变化、COM rebase、teleport、scale 替换、换父体、移除/重建。
禁止把旧样本改标成当前 tick；无法证明中间运动时保持显式未知，且相关来源不能从发现索引消失。
用结点、中途折返和高角运动场景检查 bound、velocity、support carry，而不是只比较端点。

审核 CreateContraptionColliderMixin 的注册/能力过滤与真正 GE 操作接管之间是否存在空档。
不要机械改成 isInMove；证明每个原生阶段的唯一执行者，保留 native/GE/显式拒绝三种合法路径。
普通实体、被动实体、乘客、未收到客户端几何和捕获拒绝都必须有一致行为。

下车验证不得丢弃 candidate AABB/pose/尺寸而随后提交另一形状。
要么传递候选事实，要么证明并约束最终安装就是被验证的身体；保留原生候选选择、乘客关系和回滚。
只修复证实的差异；排除的风险写出版本和场景证据。

复用已有轨迹、transport、Create seat controls。报告是否使用真实控制器、真实 world tick、真实物理子步。
不得拿手工 setAngle 或单次 helper 调用冒充整条生命周期验收。完成后停止。
```

### 提示词 C：重力／升力操作数一致与安全提交（CP-S1／CP-S2）

```text
执行 SB-02。先画清 Sable prePhysicsTick、原生 floating/balloon force、applyQueuedForces、GE gravity correction 的时序。
找出所有仍读取原生维度重力的相关预修正/阻尼表达式，解释目的后才修改。不要复制原生整方法。

先实现并验证均匀自定义场下的力、方向、单位和原生 parity；再处理空间梯度及附属运动部件。
现有 floating hook 是整刚体单点样本，不得标为分布式升力。气球 ABI 必须核对实际 Bundled 1.3.2 发行 JAR。
为相同物理点统一 query 的位置、速度、时间；若包含 child，计入 parent/child 运动各一次。
利用原生质量和升力几何，不新建竞争性的质量/惯量/浮力模型。

明确一次 GE 修正的提交边界：模拟后一个 mass/lift query incomplete，不能无声留下半套 GE 修正。
保留其他模组已经拥有的原生力，不做全局回滚；确需暂存的仅是当前步骤的 GE 派生差量。
暂不支持的范围必须显式拒绝或明确近似，不伪装 COMPLETE。

在现有 Sable/Aeronautics controls 中验证净线速度、角动量/力矩和原生力保留，包含零场、反向、梯度、解除、晚到 incomplete。
输出实际支持域和剩余限制，不只输出“Mixin 应用了”。完成后停止。
```

### 提示词 D：环境与运动集成补全（CP-S2）

```text
执行 SB-05，并复核 SB-06 与环境/pose 的交叉边界。
保留原生 travel、流体类型/效果、梯子/脚手架/坐席资格，只适配正确的空间/时间操作数。

梳理 baseTick 流体元数据、eye 检测、travel、move 后行为各自需要的采样时刻。
不能跨阶段错误复用，也不能从 solver 任意调用 captureOrCurrent 读实时世界。
修正部分遮水边界；检验父世界水和船内水的空间组合，不用单个中心点覆盖整个身体。
检查多流体选择/权重、surface velocity、进入退出、攀爬、脚手架、canStandOnFluid、伤害/窒息和自定义形状。
未知 chunk 和失败结果必须可辨識，禁止当空气或无限重复旧许可。

扩展已有环境/Sable controls；加入静态默认重力 parity 和动态边缘，不建立大型新验收框架。
记录实际角色位置、速度、状态效果、使用坐标和捕获时刻；通过画面平滑不构成物理通过。
```

### 提示词 E：去除无关来源成本并补齐前置预算（CP-S2）

```text
执行 SB-07。在修复后的行为基线上优化，不把过去遗漏的工作当成性能标准。
从 Create provider 的 mayAffect/capture/captureEnvironment 开始测候选数、trajectory 构造、排序、shape 求值、分配和预算拒绝。
去除每角色每查询对 Level 全部来源和整 contraption 方块的无条件重排；复用原生 bounds/lifecycle 做保守局部发现。
索引是派生值，不缓存与 subject 相关的实际 collision shape。未知相关来源不能被负路径跳过。
预算覆盖列表构建/排序/primitive 展开前的工作；不能等输出 collector 满才说工作有界。
相同查询内可复用 motion；禁止凭 publication revision 单独建立通用跨 tick 缓存。

执行无来源、远来源、多载具、多角色、稀疏大 bounds、高子步及全组合案例，输出原始测量和环境信息。
同时记录性能回退及原因，报告中位数/尾部/分配/候选数，不编造百分比或仅降低预算冻结世界。
通过现有长期物理不变量后再提交性能改动。
```

### 提示词 F：三个运行配置、真实客户端与文档（最终关闭 CP-S2）

```text
执行 SB-08、SB-09。保留已有 mixed-authority 和客户端终点策略，不为掩盖分歧改变产品约定。

依次运行无可选模组、sable、create、aeronautics 配置，并验证实际 mod/JAR 版本、嵌套依赖和 Mixin 是否应用。
三种 profile 已有，不重复设计；所有构建顺序执行，不能并发写共享 common/build。
先解决前面确认的缺陷，再启动真实客户端和双客户端。延伸现有 harness 到旋转父体+子体、站立/步行/跳离、来袭平台、编辑、上下车和流式生命周期。
100ms 全入站延迟保持顺序，仅覆盖该网络模型；不要把它称为任意乱序/丢包验收。
记录双方 motion evidence，明确服务器 sampled 与客户端 endpoint 的有效域。
只有实验证明必须增加传输信息时才扩展最小协议，并定义有限生命周期；不新增全局 readiness 或无限 replay。

修正 stale compatibility report 和 mod_version 回退；历史报告保留日期/提交，不用新 PASS 篡改旧结果。
保持 README/AGENTS 精简，长期契约放已有架构文档，状态/修复记录放 task report。
输出 CP-S1/CP-S2 的 PASS/FAIL/BLOCKED，逐项给出原始日志、SHA、profile、artifact hash、真实运行范围。
未运行客户端、性能或完整组合时不得声称完整兼容。禁止通过删测试、弱化阈值、停用 Mixin、关闭物理功能来关闭 checkpoint。
```

## 8. 已存在的运行命令与结果要求

以下是根据 WIP `build.gradle` 已声明任务整理的执行入口，不是本轮已经执行的命令。先在合法环境中处理依赖及用户已接受的 EULA；不得自动代替用户接受协议。每步检查退出码，失败停止；不要并行执行几个会写共享 `common/build` 的 Gradle 实例。[S30]

```powershell
# 从仓库根目录，使用已配置的 Java 21 环境
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 tasks --all --console plain --no-daemon

# 普通构建 + 无可选模组的真实服务器门禁
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification --console plain --no-daemon

# 三个 profile，逐一完成、归档后再进入下一项
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 sableCompatibilityVerification -PcompatibilityProfile=sable --console plain --no-daemon
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 sableCompatibilityVerification -PcompatibilityProfile=create --console plain --no-daemon
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 sableCompatibilityVerification -PcompatibilityProfile=aeronautics --console plain --no-daemon

# 实际图形客户端；每个 profile 分别执行并归档
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 compatibilityClientVerification -PcompatibilityProfile=aeronautics --console plain --no-daemon

# 仅准备多进程启动资料，不等于多人行为已经运行
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 prepareCompatibilityMultiplayer -PcompatibilityProfile=aeronautics --console plain --no-daemon
```

`prepareCompatibilityMultiplayer` 导出启动环境，不是完整 orchestration 门禁。需要按现有 harness 参数启动服务器与两个独立客户端，独立游戏目录和确定的通信目录／端口；若缺少启动脚本，补一个小脚本即可，不另建构建系统。

当前 profile 使用同一 `run/sable-verification` 目录，因此每种 profile 结束必须及时归档，不能以最后一次运行覆盖前两次证据。发布完整兼容结论前，至少保存：

```text
<evidence>/<sha>/<profile>/
  manifest.json              # source SHA / dirty diff / JDK / 各 mod 与 artifact hash
  command.txt
  stdout.log
  junit-results/            # 实际执行或明确 up-to-date 来源
  control-boundary-result.txt
  sable-checks-result.txt
  create-checks-result.txt   # 对相应 profile 必需
  aeronautics-checks-result.txt
  client-results/           # 实际客户端产物，不是服务端伪造
  multiplayer-results/
  performance/              # 输入、环境、原始数据、前后版本
```

状态报告按条目写：`已修复但未运行`、`真实运行通过`、`受限支持`、`FAIL`、`BLOCKED`，不只放一张绿色总表。门禁至少在合并／发布前执行；普通快速 CI 可保持较轻，但其名称和文档不能暗示完成了所有场景。

## 9. 最终验收判据

可以保留本 WIP 为开发分支，但在以下条件满足前，不应写“完整 Sable/Create/Aeronautics 兼容”：

**确认缺陷关闭。** 合法方块替换不会永久停用 GE 重力；候选几何、时间证据和失败路径具有一致所有权。

**已承诺功能贯穿原生路径。** 不是只有 common 数学、手工造 snapshot 或 helper 单测；真实物理、真实控制器、组装／修改／拆卸和客户端场景都执行。

**通用性有可解释的边界。** 不能证明的高速运动、非刚性变形、某种 scaled child、双向反作用等明确受限；但不能借“当前不支持”掩盖已承诺域的运行错误。

**性能与证据可以复现。** 无关来源不强制重复全量工作；预算包含前置工作；报告不把减少正确性计算当优化，也不把历史 PASS 当新提交验收。

**架构仍然收敛。** 修补优先落到已存在 owner、原生 lifecycle 和操作局部证据；没有新增第二套质量、刚体姿态、碰撞、网络生命周期或全局 readiness。

---

## 附录 A：证据索引

所有 GravityEngine 链接固定到审查 SHA；Sable 链接固定到 2.0.6 对应提交。源码链接说明静态事实，探针日志说明本轮运行结果；两者不能替代未运行的完整游戏测试。

- **S01** [WIP 提交与父版本差异](https://github.com/Mafuyu404/GravityEngine/compare/afd62c13519556f30089588807c23f6d4f09cd5a...2edc4269eb7c854e43babc2eb3ed8b94b895d864)
- **S02** [SableMassPoints.add / snapshot](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/compat/sable/SableMassPoints.java)
- **S03** [SableMassTrackerMixin：build 与 addBlockMass 钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/sable/SableMassTrackerMixin.java)
- **S04** [Sable 2.0.6：质量替换顺序与子步循环](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem.java)
- **S05** [Sable 2.0.6：原生 MassTracker 与方块质心](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/api/physics/mass/MassTracker.java)
- **S06** [SableGravityBridge：质量捕获、重力校正与 liftGravity](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/compat/sable/SableGravityBridge.java)
- **S07** [SableRigidCollisionProvider：采样、索引、重定位、环境](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/compat/sable/SableRigidCollisionProvider.java)
- **S08** [Sable 子步采样钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/sable/SablePhysicsSamplesMixin.java)
- **S09** [Sable 原生姿态与生命周期钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/sable/SablePublicationMixin.java)
- **S10** [RigidTrajectory 内部契约](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/common/src/main/java/cc/sighs/gravityengine/gravity/collision/RigidTrajectory.java)
- **S11** [ComposedRigidTrajectory](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/common/src/main/java/cc/sighs/gravityengine/gravity/collision/ComposedRigidTrajectory.java)
- **S12** [SampledRigidTrajectory](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/common/src/main/java/cc/sighs/gravityengine/gravity/collision/SampledRigidTrajectory.java)
- **S13** [SupportMotionTrajectory](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/common/src/main/java/cc/sighs/gravityengine/gravity/collision/SupportMotionTrajectory.java)
- **S14** [CreateRigidCollisionProvider](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/compat/create/CreateRigidCollisionProvider.java)
- **S15** [Create 原生碰撞所有权过滤](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/create/CreateContraptionColliderMixin.java)
- **S16** [MovementEnvironmentCapture](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/MovementEnvironmentCapture.java)
- **S17** [流体元数据适配](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/EntityFluidEnvironmentMixin.java)
- **S18** [流体／攀爬运动操作数适配](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/LivingEntityEnvironmentMovementMixin.java)
- **S19** [下车候选几何钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/DismountGeometryMixin.java)
- **S20** [CharacterPlacementValidation](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/CharacterPlacementValidation.java)
- **S21** [最终下车写入钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/EntityDismountMixin.java)
- **S22** [Sable 浮空块重力钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/sable/SableFloatingGravityMixin.java)
- **S23** [Aeronautics 气球重力钩子](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/compat/aeronautics/BalloonGravityMixin.java)
- **S24** [Sable 原生浮空块计算](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/physics/floating_block/FloatingBlockController.java)
- **S25** [Sable 原生升力、速度预修正与力提交顺序](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/sublevel/ServerSubLevel.java)
- **S26** [Create 源码对照：OrientedContraptionEntity](https://github.com/Creators-of-Create/Create/blob/ac0c444d9828da3453ae8cc65338e8de063286fb/src/main/java/com/simibubi/create/content/contraptions/OrientedContraptionEntity.java)
- **S27** [Create 源码对照：坐席、上下车、原生控制器](https://github.com/Creators-of-Create/Create/blob/ac0c444d9828da3453ae8cc65338e8de063286fb/src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java)
- **S28** [真实 Create 对象控制场景](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/controlTest/java/cc/sighs/gravityengine/controltest/CreateRuntimeChecks.java)
- **S29** [双客户端现有检查](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/controlTest/java/cc/sighs/gravityengine/controltest/MultiplayerClientChecks.java)
- **S30** [运行配置、依赖与门禁任务](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/build.gradle)
- **S31** [当前兼容性文档](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/docs/SABLE_COMPAT_CHECKLIST.md)
- **S32** [当前状态记录](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/docs/REFACTOR_STATUS.md)
- **S33** [当前软件版本](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/gradle.properties)
- **S34** [精确提交的 CI run](https://github.com/Mafuyu404/GravityEngine/actions/runs/37909282095)
- **S35** [主 target CI job](https://github.com/Mafuyu404/GravityEngine/actions/runs/37909282095/job/113750582631)
- **S36** [外部／原生发现范围校验](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/common/src/main/java/cc/sighs/gravityengine/gravity/collision/DynamicEntityBroadphasePolicy.java)
- **S37** [OptionalCompatibilityPlugin](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/mixin/OptionalCompatibilityPlugin.java)
- **S38** [LivingGravityIntegration.openTravel](https://github.com/Mafuyu404/GravityEngine/blob/2edc4269eb7c854e43babc2eb3ed8b94b895d864/targets/neoforge-1.21.1/src/main/java/cc/sighs/gravityengine/gravity/integration/LivingGravityIntegration.java)
