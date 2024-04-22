## Linux 内核 Patch 相关常用命令

This file includes bash commands and some config file in slides.

**DO NOT** copy any code in slides since some characters behaves abnormal in such way.

---

>#### Slides Page 3: Clone
```bash
git clone https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git
```

>#### Slides Page 4: New branch
```bash
git checkout -b ${BRANCH_NAME}
```

>#### Slides Page 7: Compile
```bash
cp XXX.config .config
make menuconfig
make config
make xconfig
make gconfig
make allnoconfig
make -j${THREADS}
```

>#### Slides Page 8: Commit
```bash
git diff
git commit -asev
```

>#### Slides Page 9: Get fixes
```bash
git log --pretty="Fixes: %h (\"%s\")" -1 ${BUG_COMMIT_ID}
```

>#### Slides Page 12: .gitconfig
```bash
[sendemail]
	smtpEncryption = ssl
	smtpServer = mail.hust.edu.cn
	smtpUser = xxx@hust.edu.cn
	smtpServerPort = 465
```

>#### Slides Page 13: format-patch
```bash
git format-patch -1
git format-patch --subject-prefix="PATCH v2" HEAD...master
```

>#### Slides Page 17: Check and Get maintainer
```bash
./scripts/checkpatch.pl ${PATCH_FILE}
./scripts/get_maintainer.pl ${PATCH_FILE}
```

>#### Slides Page 18: Send mail
```bash
git send-email ${PATCH_FILE} --to-cmd="$(pwd)/scripts/get_maintainer.pl --nogit --nogit-fallback --norolestats --nol" --cc-cmd="$(pwd)/scripts/get_maintainer.pl --nogit --nogit-fallback --norolestats --nom"
```
