# 把项目发布到 GitHub · 完整操作手册

面向第一次发布项目的人。从零到仓库上线，每一步都有可直接复制的命令。

> 环境：Windows + PowerShell。本项目路径假设为 `D:\112233\xiaoman-ledger`，请按你的实际路径替换。

---

## 目录

- [第 0 步 · 准备](#第-0-步--准备)
- [第 1 步 · 配好身份](#第-1-步--配好身份)
- [第 2 步 · 本地建仓并提交](#第-2-步--本地建仓并提交)
- [第 3 步 · 在 GitHub 上建空仓库](#第-3-步--在-github-上建空仓库)
- [第 4 步 · 连上远程并推送](#第-4-步--连上远程并推送)
- [第 5 步 · 验证](#第-5-步--验证)
- [第 6 步 · 日常改代码的循环](#第-6-步--日常改代码的循环)
- [进阶 A · 用 GitHub Pages 让网页版直接上线](#进阶-a--用-github-pages-让网页版直接上线)
- [进阶 B · 用 Release 分发 APK](#进阶-b--用-release-分发-apk)
- [进阶 C · 用 Actions 自动打包 APK](#进阶-c--用-actions-自动打包-apk)
- [常见报错排查](#常见报错排查)
- [安全铁律](#安全铁律)

---

## 第 0 步 · 准备

### 0.1 你需要三样东西

| 东西 | 状态 | 说明 |
| --- | --- | --- |
| Git | ✅ 已装（`git version 2.52.0.windows.1`） | 本机已就绪，无需操作 |
| GitHub 账号 | ❓ 待确认 | 没有就去 <https://github.com/signup> 注册 |
| 网络 | ❓ | 国内访问 GitHub 可能不稳，见下方说明 |

### 0.2 关于网络

如果 `git push` 卡住或报 `Failed to connect to github.com port 443`，常见做法是给 Git 配代理（**只在你确实有代理客户端时**）：

```powershell
# 假设本地代理端口是 7890（Clash/V2Ray 常见默认值，按实际改）
git config --global http.proxy http://127.0.0.1:7890
git config --global https.proxy http://127.0.0.1:7890

# 取消代理
git config --global --unset http.proxy
git config --global --unset https.proxy
```

注意：这是给 **Git** 配代理，不影响系统其它程序。如果没装代理客户端，别照抄这段。

---

## 第 1 步 · 配好身份

你已经配过了，先确认一下：

```powershell
git config --global user.name
git config --global user.email
```

本机当前的值是：

```
jiajun-youth
3485584137@qq.com
```

### ⚠️ 先处理这个隐私问题

**每一次 `git commit` 都会把你的邮箱永久写进提交历史，并公开在你 push 到的任何仓库里。** 一旦推上去，任何人 `git log` 就能看到，而且**删不掉**（可以改历史，但已经被人克隆/被搜索引擎收录的部分收不回来）。

如果你不想让 QQ 邮箱挂在公开仓库上，用 GitHub 提供的匿名邮箱。先去 <https://github.com/settings/emails> 勾选 **Keep my email addresses private**，页面会给你一个形如：

```
12345678+jiajun-youth@users.noreply.github.com
```

然后改用这个：

```powershell
git config --global user.email "12345678+jiajun-youth@users.noreply.github.com"
```

或者只想改这个仓库（不影响别的项目）：

```powershell
cd D:\112233\xiaoman-ledger
git config user.email "12345678+jiajun-youth@users.noreply.github.com"
```

### 1.1 顺手把默认分支名设成 main

GitHub 现在默认用 `main`，老 Git 默认还是 `master`，统一一下省得后面混：

```powershell
git config --global init.defaultBranch main
```

### 1.2 处理 Windows 换行符（重要）

Windows 用 `CRLF`，Linux/macOS 用 `LF`。不设这个，你一提交就会出现「整个文件都改了」的假 diff：

```powershell
git config --global core.autocrlf true
```

---

## 第 2 步 · 本地建仓并提交

进到项目目录：

```powershell
cd D:\112233\xiaoman-ledger
```

### 2.1 初始化仓库

```powershell
git init
```

输出 `Initialized empty Git repository in D:\112233\xiaoman-ledger\.git\` 就成功了。

### 2.2 先看清 `.gitignore` 在挡什么

这一步别跳。**提交之前先确认没有把密钥、APK、导出的账单带进去**：

```powershell
git status
```

你要看到的应该是这样的（`Untracked files` 里只有源码和文档）：

```
Untracked files:
        .gitignore
        LICENSE
        README.md
        docs/
        index.html
        android/
```

**不应该**出现在列表里的：

- `*.jks` / `*.keystore` / `keystore.properties` —— 签名密钥，泄露等于身份被盗
- `*.apk` / `*.aab` —— 二进制产物，不该进源码仓库
- `*.csv` / `*.jsonl` —— 导出的真实账单，含你的消费记录
- `android/build/` / `.gradle/` —— 构建缓存，几百 MB

如果这些真的出现了，说明 `.gitignore` 没生效或规则有遗漏，先修 `.gitignore` 再继续。**文件一旦被 commit，`.gitignore` 就管不住它了**，得用 `git rm --cached` 摘出来。

### 2.3 加进暂存区

```powershell
git add .
```

再确认一遍将要提交的内容：

```powershell
git status --short
```

`A` 开头的是新增文件。核对一下文件数量和清单是否符合预期。

### 2.4 提交

```powershell
git commit -m "feat: 小满账本 v1.2.0 首次开源"
```

### 2.5 万一提交错了（还没推送）

```powershell
# 只撤出暂存区，文件内容不动
git reset

# 连 commit 一起撤销，但保留所有改动
git reset --soft HEAD~1

# 彻底回到干净状态（危险！会丢改动）
git reset --hard HEAD~1
```

只要**还没 push**，一切都好救。push 之后就是另一回事了。

---

## 第 3 步 · 在 GitHub 上建空仓库

浏览器打开 <https://github.com/new>。

### 关键设置

| 字段 | 填什么 | 为什么 |
| --- | --- | --- |
| **Repository name** | `xiaoman-ledger` | 和本地目录同名，清晰 |
| **Description** | `离线优先的记账工具 · 单文件网页应用 + Android WebView 外壳` | 会显示在仓库页顶部 |
| **Public / Private** | 想让人看到就 **Public**；只想自己备份就 **Private** | Public 才是真正的「发布」 |
| **Add a README file** | ❌ **不要勾** | 你已经本地有 README 了，勾了会产生一次「无关的历史」，push 时直接冲突 |
| **Add .gitignore** | ❌ **不要选** | 同上，你已经有自己的 `.gitignore` |
| **Choose a license** | ❌ **不要选** | 你已经放了 `LICENSE` 文件 |

> **这一步最容易踩的坑**：在网页上勾了 README / gitignore / license，仓库就有了一个提交，而你本地也有自己的提交，两条历史没有共同祖先，`git push` 会被拒（`fetch first` / `non-fast-forward`）。保持**空仓库**，就不会遇到。

### 建完之后

页面会显示一堆「快速开始」命令。**先别照抄**，下一页我们会用稍有不同的版本（因为要处理分支名）。

---

## 第 4 步 · 连上远程并推送

### 4.1 认证方式二选一

GitHub 从 2021 年起**不再接受账号密码**做 Git 操作。必须用下面两种之一。

#### 方式 A：个人访问令牌（PAT）— 简单，推荐新手

1. 打开 <https://github.com/settings/tokens>
2. 点 **Generate new token** → 选 **Fine-grained token**（或经典版 **Tokens (classic)**）
3. 经典版勾选权限：**`repo`**（完整仓库读写）。如果用 fine-grained，就选「Only select repositories」→ 选中 `xiaoman-ledger` → Repository permissions → **Contents: Read and write**
4. 设置有效期（建议 90 天，到期再换）
5. 生成后**立刻复制**那串 `ghp_...` 开头的字符串——**页面关掉就再也看不到了**

首次 `push` 时会弹窗要账号密码：

```
Username: 你的 GitHub 用户名
Password: 粘贴 PAT（不是账号密码！）
```

Windows 会把凭据存进「凭据管理器」，之后不用再输。

> ⚠️ **如果公司电脑或共享电脑**，别让 Git 记住：
> ```powershell
> git config --global credential.helper ""
> ```

#### 方式 B：SSH 密钥 — 更安全，长期用

```powershell
# 1. 生成密钥（一路回车即可，密码短语可留空）
ssh-keygen -t ed25519 -C "你的GitHub邮箱"

# 2. 把公钥内容复制到剪贴板
Get-Content "$env:USERPROFILE\.ssh\id_ed25519.pub" | Set-Clipboard
```

3. 打开 <https://github.com/settings/keys> → **New SSH key** → 粘贴 → 保存
4. 测试连通性：

```powershell
ssh -T git@github.com
```

看到 `Hi 你的用户名! You've successfully authenticated` 就成了（提示 `does not provide shell access` 是正常的）。

> 如果 SSH 报 `Connection timed out`，试 443 端口：
> ```powershell
> # 在 ~/.ssh/config 里加：
> # Host github.com
> #   HostName ssh.github.com
> #   Port 443
> #   User git
> ```

### 4.2 关联远程仓库

把 `你的用户名` 换成实际的 GitHub 用户名。

**方式 A（HTTPS）**：

```powershell
git remote add origin https://github.com/你的用户名/xiaoman-ledger.git
```

**方式 B（SSH）**：

```powershell
git remote add origin git@github.com:你的用户名/xiaoman-ledger.git
```

检查是否关联成功：

```powershell
git remote -v
```

应该看到两行（fetch / push）指向同一个地址。

### 4.3 确认分支名是 main

```powershell
git branch --show-current
```

- 输出 `main` → 直接进 4.4
- 输出 `master` → 改个名：

```powershell
git branch -M main
```

### 4.4 推送

```powershell
git push -u origin main
```

`-u` 的作用是把本地 `main` 和远程 `origin/main` 绑定，**之后只需要敲 `git push`**。

成功的样子：

```
Enumerating objects: 12, done.
Counting objects: 100% (12/12), done.
Writing objects: 100% (12/12), 68.42 KiB | 1.20 MiB/s, done.
To https://github.com/你的用户名/xiaoman-ledger.git
 * [new branch]      main -> main
branch 'main' set up to track 'origin/main'.
```

**去浏览器刷新你的仓库页面，文件应该都在了。**

---

## 第 5 步 · 验证

逐项过一遍，确认发布质量：

- [ ] 打开 `https://github.com/你的用户名/xiaoman-ledger`
- [ ] README 在页面下方正常渲染（标题、表格、代码块都在）
- [ ] 文件列表里有 `index.html`、`LICENSE`、`.gitignore`、`docs/`、`android/`
- [ ] 点开 `index.html`，右上角能看到行数（约 8,503 行）
- [ ] **没有** `.jks` / `.apk` / `.csv` / `build/` 之类的文件
- [ ] 页面右侧显示 MIT 协议徽章（因为有 `LICENSE` 文件，GitHub 会自动识别）
- [ ] 提交时间、作者名正确

```powershell
# 本地也可以确认远程状态
git log --oneline -1
git status
```

`git status` 应该显示：

```
On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean
```

---

## 第 6 步 · 日常改代码的循环

发布只是开始。之后每次改动都是这个三步循环：

```powershell
# 1. 看改了什么
git status

# 2. 加进暂存区（. 表示当前目录全部；也可以指定单文件）
git add .

# 3. 提交并推送
git commit -m "fix: 修复统计页月份切换后图表不刷新"
git push
```

### 提交信息怎么写

推荐 Conventional Commits，一行说清「做了什么」：

| 前缀 | 用在什么时候 | 例子 |
| --- | --- | --- |
| `feat:` | 新功能 | `feat: 增加按周统计` |
| `fix:` | 修 bug | `fix: 修复空账本时统计页报错` |
| `docs:` | 只改文档 | `docs: 补充 APK 签名说明` |
| `style:` | 格式、空格，不影响逻辑 | `style: 统一缩进为 2 空格` |
| `refactor:` | 重构，行为不变 | `refactor: 抽出金额格式化函数` |
| `chore:` | 杂项、依赖、配置 | `chore: 更新 .gitignore` |

### 想看自己改了什么，但又不想提交

```powershell
git diff                  # 工作区 vs 暂存区
git diff --staged         # 暂存区 vs 上次提交
git log --oneline -10     # 最近 10 次提交
```

### 改错了想丢弃某个文件的改动（危险）

```powershell
git restore 文件名
```

**这会永久丢弃该文件未提交的修改**，没有回收站。

### 分支与 Pull Request

大于一件事的改动，走分支：

```powershell
git checkout -b feat/weekly-stats     # 建分支并切过去
# ...改代码...
git add .
git commit -m "feat: 增加按周统计"
git push -u origin feat/weekly-stats
```

推送后 GitHub 会在仓库页顶部弹出一条 **Compare & pull request** 按钮，点它就能开 PR。
PR 的意义：改动先在独立分支上评审，通过后再合并进 `main`，`main` 永远是可用的。

---

## 进阶 A · 用 GitHub Pages 让网页版直接上线

这个项目**特别适合** Pages——根目录就一个 `index.html`，没有任何构建步骤。

1. 仓库页 → **Settings** → 左侧 **Pages**
2. **Source** 选 `Deploy from a branch`
3. **Branch** 选 `main`，目录选 `/ (root)`
4. 点 **Save**
5. 等 1～2 分钟，页面顶部会出现：

```
Your site is live at https://你的用户名.github.io/xiaoman-ledger/
```

**现在任何人都能直接用浏览器打开这个小账本了**，不需要安装任何东西。

### 注意事项

- Pages 是**公开**的（即使仓库是 Private，Pages 也能配置可见性，但免费版通常要求 Public）。
- `localStorage` 是**按域名隔离**的：`你的用户名.github.io` 上的账本，和本地 `file://` 打开的账本**不互通**。想迁移数据，用「导出 JSON」→ 在网页版「导入」。
- 改完代码 `git push` 后，Pages 会自动重新部署，等 1 分钟左右刷新即可。

### 自定义域名（可选）

在 `Settings → Pages → Custom domain` 填你的域名，然后到域名服务商加一条 `CNAME` 记录指向 `你的用户名.github.io`。

---

## 进阶 B · 用 Release 分发 APK

**不要把 `.apk` 提交进 Git 仓库**——二进制文件会让仓库体积永久膨胀，且每次改版都存一份历史。正确做法是用 **Release** 附件。

1. 仓库页右侧 → **Releases** → **Create a new release**
2. **Choose a tag** 输入 `v1.2.0` → 选 **Create new tag on publish**
3. **Release title**：`小满账本 v1.2.0`
4. **Describe this release** 写更新说明：
   ```markdown
   ## 新增
   - 自动记账支持自定义分类规则

   ## 修复
   - 修复统计页在 2 月切换时的日期越界

   ## 安装说明
   1. 下载 `xiaoman-ledger-1.2.0.apk`
   2. 允许「安装未知来源应用」
   3. 安装前建议先导出 JSON 备份（覆盖安装可能失败，需卸载重装）
   ```
5. 把 APK **拖进** 「Attach binaries」区域
6. 点 **Publish release**

之后别人就能在 `https://github.com/你的用户名/xiaoman-ledger/releases` 下载了。

> **别把签名密钥传上去。** Release 只放 APK，`.jks` 永远留在本地（或用 GitHub Secrets 存）。

---

## 进阶 C · 用 Actions 自动打包 APK

每次打 tag 时自动编译并附到 Release 上。在项目里建 `.github/workflows/release-apk.yml`：

```yaml
name: Build APK

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Sync web app into assets
        run: cp index.html android/app/src/main/assets/www/index.html

      - name: Build debug APK
        run: |
          cd android
          chmod +x gradlew
          ./gradlew assembleDebug --no-daemon

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: android/app/build/outputs/apk/debug/*.apk

      - name: Attach to release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v2
        with:
          files: android/app/build/outputs/apk/debug/*.apk
```

**要点**：

- `android/` 目录必须先能在 Linux 上构建（`gradlew` 要有可执行位：`git update-index --chmod=+x android/gradlew`）
- 签名密钥放到 **Settings → Secrets and variables → Actions**，用 `${{ secrets.KEYSTORE_BASE64 }}` 引用，**绝不要**写进 workflow 文件
- 前面说过 `android/` 是逆向重建的参考实现，**首次跑很可能要修编译错误**——先在本地跑通再上 CI

---

## 常见报错排查

### `remote: Support for password authentication was removed`

用了账号密码。换成 **PAT**（见 4.1 方式 A），密码栏填 `ghp_...` 那串。

### `failed to push some refs` / `Updates were rejected because the remote contains work that you do not have locally`

远程有你本地没有的提交（通常因为建仓库时勾了 README）。

```powershell
# 拉下来合并（会生成一个 merge commit）
git pull origin main --allow-unrelated-histories
git push
```

或者**如果你确定远程那个提交不要了**（新建的空仓库才适用）：

```powershell
git push -u origin main --force
```

`--force` 会覆盖远程历史，**只在刚建的空仓库上用**，别在已有协作的仓库上碰。

### `error: src refspec main does not match any`

说明你还没有任何提交。

```powershell
git add .
git commit -m "init"
```

### `fatal: not a git repository`

当前目录不对，或缺 `git init`。

```powershell
cd D:\112233\xiaoman-ledger
git status
```

### `Permission denied (publickey)`

SSH 密钥没配好。先验证：

```powershell
ssh -T git@github.com
```

不行就回去看 4.1 方式 B。或者干脆切回 HTTPS：

```powershell
git remote set-url origin https://github.com/你的用户名/xiaoman-ledger.git
```

### 中文文件名显示成 `\344\270\255\346\226\207`

```powershell
git config --global core.quotepath false
```

### 提交里混进了不该提交的文件

```powershell
# 从 Git 移除但保留本地文件
git rm --cached 文件名
git commit -m "chore: 移除误提交的文件"
```

如果已经 push 了，历史里还留着。要彻底清除得用 `git filter-repo`（比 `filter-branch` 快且安全）：

```powershell
pip install git-filter-repo
git filter-repo --path 敏感文件 --invert-paths
git push --force
```

**未 push 之前处理永远比 push 之后简单一百倍。**

### 仓库体积忽然变得很大

```powershell
# 找出最大的几个文件
git rev-list --objects --all |
  git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' |
  Sort-Object { [int]($_ -split ' ')[2] } -Descending |
  Select-Object -First 10
```

通常是误提交了 `build/` 或 `.apk`。按上一条清掉。

### `warning: LF will be replaced by CRLF`

只是提醒，不是错误。已在 1.2 配过 `core.autocrlf true`。

---

## 安全铁律

发布前对照这张表，任何一条不过就别 push：

| # | 铁律 | 检查方法 |
| --- | --- | --- |
| 1 | **签名密钥不进 Git** | `git ls-files \| Select-String "jks\|keystore"` 应该无输出 |
| 2 | **密码、Token 不进 Git** | `git ls-files \| Select-String "\.env\|secret\|password"` |
| 3 | **真实个人数据不进 Git** | 检查有没有 `.csv` / `.jsonl` / 导出备份 |
| 4 | **API Key 用环境变量或 Secrets** | 源码里搜 `sk-`、`ghp_`、`AIza` |
| 5 | **提交前必看 `git status`** | 每次都看，这是最便宜的保险 |
| 6 | **个人邮箱想清楚再公开** | 见第 1 步的匿名邮箱设置 |
| 7 | **别人给的代码不要直接 push** | 尤其 `postinstall` 脚本类项目 |

### 已经泄露了怎么办

1. **立刻撤销**：GitHub → Settings → Developer settings → Tokens → 删除该 token；云服务商的 key 到控制台禁用
2. **别指望删提交**：改历史、force push 都无法召回已被克隆或抓取的内容
3. **当成已公开处理**：轮换所有相关凭据
4. GitHub 的 secret scanning 会自动扫描公开仓库里的已知格式密钥，命中会给你发邮件——但那是补救，不是防护

---

## 一页速查

```powershell
# 首次发布
cd D:\112233\xiaoman-ledger
git init
git add .
git status                      # ← 必看
git commit -m "feat: 首次开源"
# 网页建空仓库（不勾 README / gitignore / license）
git remote add origin https://github.com/你的用户名/xiaoman-ledger.git
git branch -M main
git push -u origin main

# 日常
git add .
git commit -m "fix: 说明改了什么"
git push

# 分支 + PR
git checkout -b feat/xxx
git add . && git commit -m "feat: xxx"
git push -u origin feat/xxx        # 然后去网页开 PR

# 打版本
git tag v1.2.0
git push origin v1.2.0
```

---

发布完成后，建议再做的三件事：

1. **开启 GitHub Pages** —— 网页版立刻可用（进阶 A）
2. **发一个 Release** —— 给别人一个明确的下载入口（进阶 B）
3. **在 README 顶部加截图** —— 比一千行说明都管用。手机截图放进 `docs/screenshots/`，然后 `![](docs/screenshots/overview.png)` 引用
