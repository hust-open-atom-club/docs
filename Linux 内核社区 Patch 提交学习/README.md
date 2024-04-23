# Getting Start：Linux 内核

# 任务领取

任务列表的链接在 [HUST OS Kernel Contribution Team](https://ixy0caf7465.feishu.cn/wiki/SyywwBKU5iBzpJkOyk2cMKLin1c?from=from_copylink) 的**新手任务**数据表

> 任务列表根据 `linux-next` 仓库中 `next-20231013` 生成

任务列表中的任务描述是扫描工具的输出，其中可能含有一些格式信息，如任务列表中第一个便是 `checkpatch.pl`（一种检测内核 patch 格式的工具）的输出：

```
ERROR: space required after that ',' (ctx:VxV)
#119: FILE: ../linux-next/drivers/media/usb/dvb-usb/dibusb-mc-common.c:119:
+       u8 a,b;
            ^
ERROR: space required after that ',' (ctx:VxV)
#127: FILE: ../linux-next/drivers/media/usb/dvb-usb/dibusb-mc-common.c:127:
+               dibusb_read_eeprom_byte(adap->dev,0x7E,&a);
                                                 ^
```

其中包括有问题代码的具体位置等信息，本例中 `checkpatch``.pl` 告诉我们代码中相应的位置函数参数列表的逗号后面需要添加一个空格，请根据信息编写 patch。

# 获取内核代码仓库

**注**：请大家使用 `linux-next` 仓库。使用 `linux` 主线仓库经常出现我们写好 patch 发送之后发现已经被人修复的情况，虽然 `linux-next` 也不能避免但总会好一些。

使用 `git clone` 获取源代码，推荐使用俱乐部组建的镜像站（<https://mirrors.hust.edu.cn/>）：

```shell
git clone https://mirrors.hust.college/git/linux-next.git
```

也可以使用 linux 官方的 git 仓库：<https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/>

不会使用 git 的同学请自行搜索学习相关的指令和概念，git 是一种流行的版本管理工具，在开源社区协作的工作中有着重要的作用，请务必熟练掌握基本的用法

# 修改代码生成 patch

## 修复代码问题

克隆好代码仓库后，需要根据问题描述修复问题，并编写 patch。

根据问题的描述，定位到相应代码处，继续以第一个任务为例，可以看到：

```c
int dibusb_dib3000mc_tuner_attach(struct dvb_usb_adapter *adap)
{
        struct dibusb_state *st = adap->priv;
        u8 a,b;
        u16 if1 = 1220;
        struct i2c_adapter *tun_i2c;

        // First IF calibration for Liteon Sticks
        if (le16_to_cpu(adap->dev->udev->descriptor.idVendor) == USB_VID_LITEON &&
            le16_to_cpu(adap->dev->udev->descriptor.idProduct) == USB_PID_LITEON_DVB_T_WARM) {

                dibusb_read_eeprom_byte(adap->dev, 0x7E, &a);
                dibusb_read_eeprom_byte(adap->dev,0x7F,&b);
```

> 注：由于 `linux-next` 仓库变化很快，实际拿到的内核代码行数可能与任务列表中指示的存在差异。如果找不到的话，可以在同一文件内进行搜索。当然，不排除该问题已经被修复/相关代码直接被删除的可能。

根据内核代码风格指南（见内核文档 [Linux kernel coding style](https://www.kernel.org/doc/html/latest/process/coding-style.html)）修改代码，在相应逗号后面加上空格。

## 编写 Commit message

在 commit 之前请记得配置自己的名称与邮箱，请使用英文真实姓名并使用 hust 邮箱。

修改完成代码之后，使用 git 进行代码 commit：

```bash
git commit -asev
```

> 注意这里添加了 `-s` 的 option，目的是为了在 commit message 中添加签名（Signed-off-by 字段），正常情况下，上述指令会打开编辑器，且其中包含如下内容：
> 
> Signed-off-by: Yalong Zou &lt;<yalongz@hust.edu.cn>&gt;

Commit Message 的编写也需要遵循内核的规范（<https://www.kernel.org/doc/html/latest/process/submitting-patches.html>）。我们主要注意以下几点：

1. 一行不要超过 75 个字符，如果太长的话需要换行
2. 每一段结束后需要留一个空行
3. Commit message 的标题（也就是第一行话）需要加上 subsystem 前缀。前缀一般是 `area: driver: change` 的形式，但存在很多例外。你可以使用 `git log -- <文件名>` 查看关于某一个文件的历史 commit，并且可以看到相应的 commit message。参考其他人进行 subsystem 的编写是最有效的办法。

请在 commit message 说明具体问题的描述，可以将检测工具的输出粘贴进去，也可以进行简单的文字描述。例如：

```
media: dvb: add space after comma to fix coding style

checkpacth complains that:

ERROR: space required after that ',' (ctx:VxV)
#119: FILE: ../linux-next/drivers/media/usb/dvb-usb/dibusb-mc-common.c:119:
+       u8 a,b;
            ^
ERROR: space required after that ',' (ctx:VxV)
#127: FILE: ../linux-next/drivers/media/usb/dvb-usb/dibusb-mc-common.c:127:
+               dibusb_read_eeprom_byte(adap->dev,0x7E,&a);
                                                 ^
Fix it by adding required spaces after the commas to fix the coding
style issue.

Signed-off-by: Yalong Zou <yalongz@hust.edu.cn>
```

## 生成 patch

使用 `git format-patch -1` 将刚刚的 commit 生成一个 patch。生成 patch 后请使用 `checkpatch` 对生成的 patch 进行一些格式检查：

```
# 在内核代码仓库目录下
./script/checkpatch.pl <生成的 patch 的文件名>
```

不要忽略任何一个 warning！请根据输出的指示对 patch 进行修改。

# 配置 `git send-email`

1. 首先，为你学校邮箱设置一个独立密码。

社团要求大家使用 hust 邮箱进行开源贡献。如果需要使用 `git send-email` 等第三方客户端，需要为 hust 邮箱设置独立密码。

设置好密码之后，在 `~/.gitconfig` 或 `~/.config/git/config` 文件中添加一下内容来配置 `git send-email`

```bash
[sendemail]
        smtpEncryption = ssl
        smtpServer = mail.hust.edu.cn
        smtpUser = <你的邮箱地址>
        smtpServerPort = 465
        smptAuth = LOGIN
```

# 发送 patch 至社团内部的 Linux 审核小组进行内审

在确认 patch 没有问题后，请将该 patch 发送至慕冬亮老师的邮箱同时抄送到我们内部审核使用的邮件列表，发送之后加入了该 google group 的成员均可以看到你的 patch。

```
git send-email --to=dzm91@hust.edu.cn --cc=hust-os-kernel-patches@googlegroups.com <生成的 patch 的文件名>
```

发送至内部审核小组之后，请等待审核小组的回复，如果审核小组对 patch 提出修改意见，请根据意见重新编写生成 patch 并再发送至审核小组。确认没有问题之后，再发送至 Linux 内核社区。

# 发送 patch 至 Linux 内核社区

使用内核的 `get_maintainer.pl` 脚本获取 maintainer 列表，可以使用如下命令发送邮件

```
git send-email \
  --to-cmd="`pwd`/scripts/get_maintainer.pl --nogit --nogit-fallback --norolestats --nol" \
  --cc-cmd="`pwd`/scripts/get_maintainer.pl --nogit --nogit-fallback --norolestats --nom" \
  --cc=hust-os-kernel-patches@googlegroups.com
  <生成的 patch 的文件名>
```

> 该命令也会将 patch 抄送至内部审核邮件列表，方便我们对 patch 的状态进行追踪

# Further Reading

- [传统操作系统内核社区贡献指引](./传统操作系统内核社区贡献指引.md)
- [内核 Patch FAQ](./Linux%20内核%20Patch%20FAQ.md)
- [Submitting patches: the essential guide to getting your code into the kernel](https://www.kernel.org/doc/html/latest/process/submitting-patches.html)
