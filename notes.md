# 流水线高级控制：冒险, 前递与分支预测

> 统一假设为 IF/ID/EXE/MEM/WB 五级流水线

本文从几个例子出发, 分析构造一条合格的流水线所需要考虑的高级控制问题以及它们所需要引入的硬件. 
以便于在实际构造流水线硬件时, 能更加地从容不迫. 

## 1. 数据冒险与前递

### 1.1 RAW (Read After Write)

考虑两条指令序列：

```assembly
def x1 ...
use1 x1 ...
use2 x1 ...
use3 x1
```

流水线状态如下所示：

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | def |     |     |     |     | 0  |
| 1  | use | def |     |     |     | 4  |
| 2* |use2 |use1 | def |     |     | 8  |
| 3* |     |use2 |use1 | def |     | 12 |
| 4  |     |use3 |use2 |use1 | def | 16 |

注意看 `t = 2`, 
此时 use1 指令在 ID 阶段需要获得 def 定义的寄存器的值, 
但是它还没有到 WB 阶段, 所以不能从 RF 中获取. 
我们可以直接从 ALU 的 result 拉线出来作为 use1 指令的某个操作数, 
这就产生了前递. 

然后注意看 `t = 3`, 
此时 use2 指令在 ID 阶段需要获得 def 定义的寄存器的值, 
但是同样 def 还没有到 WB 阶段, 所以不能从 RF 中获取. 
因此我们需要从 EXE/MEM 中拉线出来作为 use2 指令的某个操作数, 
这也是前递的一种可能. 

当 `t = 4` 时, def 指令到达 WB 阶段, 此时它的值已经在 RF 中了, 
因此 use3 指令可以直接从 RF 中获取 def 的值, 不再需要前递. 

所以要做前递, 首先需要在 ID/EXE 和 EXE/MEM 中保存解码出的 rd 的信息,
然后依次判断:

1. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
2. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE/MEM 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

注意, 我们必须优先考虑 ID/EXE 中的 rd, 因为它是最新的,
当两条 def 指令同时写相同的寄存器时, 
我们必须保证我们前递的是后面的 def 的结果, 而不是前面的. 

这也许可以算一种 WAW 冲突.

### 1.2 WAR (Write After Read)

由于我们在 ID 中便获取 rs1/rs2, 而到 WB 才写回,
中间相差 3 个周期, 不可能出现 WAR 的情况.

### 1.3 WAW (Write After Write)

在 RAW 中已经考虑完全了.

## 2. 访存导致的停顿

考虑指令序列如下：

```assembly
ld x1 ...
use1 x1 ...
use2 x1 ...
use3 x1 ...
use4 x1 ...
```

流水线状态如下所示：

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | ld  |     |     |     |     | 0  |
| 1  |use1 | ld  |     |     |     | 4  |
| 2* |!use2|!use1|%ld  |     |     | 8  |
| 3* | use2| use1| nop | ld  |     | 8  |
| 4  | use3| use2| use1| nop | ld  | 12 |

注意看 `t = 2`,
此时我们发现 use1 指令在 ID 阶段需要获得 ld 定义的寄存器的值,
但是我们发现 ld 指令还没有到达 WB 阶段, 所以不能从 RF 中获取.
更糟糕的是, 由于 ld 指令需要访存, 我们并不能直接像普通运算一样前递 ALU 算出的值,
所以我们不得不停顿流水线, 并插入一个气泡, 等待 ld 至少到达 MEM 阶段.
一旦 ld 到达了 MEM 阶段, 我们就可以前递 MEM 的输出到 ID 阶段供 ID/EXE 接收.

为此, 我们需要做几件事:

1. 让 IF/ID 和 PC 拥有 "下周期保持当前周期值" 的功能
2. 让 ID/EXE 输出 "nop" 的功能
3. MEM -> ID 的前递

其中, 1 可以通过更改流水线寄存器的中寄存器的写入实现:

```scala
when (!io.pipe.stall) {
  reg := io.in
}
```

而 2 可以通过修改 ID/EXE 的输出实现:

```scala
when (io.pipe.nop) {
    out.rfw_en := Controls.rfw_en.no
    out.memw_en := Controls.memw_en.no
} .otherwise {
    out.rfw_en := reg.rfw_en
    out.memw_en := reg.memw_en
}
```

而 3 则需要修改我们的前递逻辑为:

1. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同:
   1. 当前指令是否为 ld, 如果是, 则前递 MEM 中的 memr_data
   2. 否则, 前递 EXE/MEM 中的 alu_result
2. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

并且还需要在 ID/EXE 和 EXE/MEM 中保存解码出的 is_ld 信号.

## 3. 分支预测

分支预测技术是为了在 IF 阶段就快速判断下周期 pc 取值是什么而引入的技术.
它分为两部分, 一部分是预测, 另一部分是检查.
预测部分在 IF 阶段做, 直接向 pc 提供合适的 npc 值, 同时将预测结果传递到流水线中.
检查部分在 EXE 阶段做. 此时使用流水中的预测结果与 alu flag 进行比较,
如果成功则回报且继续执行; 如果失败则回报且清空后面两个流水线.
回报传递至分支预测器中用于更新, 而分支预测器的结果直接在 IF 阶段被取用用于预测 npc.

在 RISC-V 中, 会修改 PC 的指令有:

- jal
- jalr
- beq, bne, blt, ble, bgt, bge

它们分别会:

- jal 指令会直接预测到正确结果, 因为它总是跳转
- jalr 指令总是会预测失败, 直到 EXE 阶段再根据 rs1 和 imm 计算出正确的 npc
- bxx 指令的预测成功率取决于分支预测器有多好

对于两条跳转指令连续的情况, 因为在前一条指令失败的时候, 
它会清空流水线, 从而清空后面跳转指令传递的预测结果, 因此在预测结果上不会有影响.
而由于对分支预测器的训练是在 EXE 阶段才做的, 所以它们对分支预测器也不会有影响.

在流水线寄存器中传递的相关信息有:

- pc: 该阶段的指令对应的 pc
- br_pred: 对该次跳转是否被采纳的预测

那么在 EXE 阶段, 我们可以得到 `br_real`, 它表示该次跳转是否真的发生了.
我们需要将 `br_valid`, `pred_succ` 传回分支预测器, 以便于更新分支预测器的状态.
如果 `!pred_succ`, 则要 ID/EXE 持续两周期 nop 清空前面两个流水线,
并且回传 `ID/EXE.pc/reg_rs1` 和 `ID/EXE.imm` 用于计算正确的 npc. 

## 总结

为了解决冒险, 我们需要引入前递模块与气泡功能, 后者需要更多的控制信号.

我们需要加入这些控制信号:

- ID/EXE: rd, is_ld, br_pred
- EXE/MEM: rd, is_ld

我们需要加入这些功能:

- 前递模块: 新的控制与两个选择器
- 分支预测模块: 给出预测值, 接收 alu flag 并给出预测是否正确
- PC 和 IF/ID 的当前周期停顿功能: stall
- ID/EXE 的 nop 与 next_nop 功能: nop, next_nop

前递模块的逻辑如下:

1. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
2. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同:
   1. EXE/MEM 中的当前指令是否为 ld, 如果是, 则前递 MEM 中的 memr_data
   2. 否则, 前递 EXE/MEM 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

停顿与 nop 的逻辑如下:

- `stall`: (ID/EXE.is_ld) 且 (ID/EXE.rd 与 ID 阶段的 rs1 或 rs2 相同)
- nop: `stall` 或 `pred_fail`
- next_nop: `pred_fail`

