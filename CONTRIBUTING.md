# 如何为 HUST SecLab Team Docs 贡献文档

HUST SecLab Docs （以下简称文档站）使用 Hugo 作为静态网站生成器。

首先安装 Hugo，并保证已加入了环境变量，参考 [安装 Hugo](https://www.gohugo.org/doc/overview/installing/)

由于使用了第三方主题，因此本地预览需要更新子模块：

```bash
git submodule update --init
```

## 添加新文档

为了保证图片预览在本地和线上均能正常显示，文档站使用了比较特殊的目录结构：

```
content.zh
├── docs # 文档站位于 GitHub Pages 的 docs 路径下，因此所有文档均放在 docs 目录下
│   └── Kernel-FAQ # 每篇文档对应一个文件夹
│       ├── images # 文档中的图片放在 images 目录下
│       │   ├── commit-title.png
│       │   └── reviewed-by.png
│       └── index.md # 这里是文档的正文
└── _index.md # 文档站的总目录，使用 README.template.md 自动生成
```

所以，新建一篇文档需要在 `content.zh/docs` 目录下新建一个文件夹，文件夹名即为文档的标题，然后在文件夹下新建 `index.md` 文件，文件内容即为文档的正文。

## 提交修改

准备了一个 Makefile 用于构建文档主页的 README 和正常的构建。提交前务必运行`make build`以确保文档能正常生成。

```bash

make build-readme # 构建文档主页的 README

make build # 构建文档，建议在提交前运行一次以确保文档能正常生成

make serve # 本地预览文档站，访问 http://localhost:1313/docs 注意这里的路径是 /docs

```

## 修改 README

`README.md` 和 `content.zh/_index.md` 都是通过`README.md.template`自动生成的，因此不要直接修改 `README.md`，而是修改 `README.template.md`，然后运行 `make build-readme` 以生成新的 `README.md`.

## 配置 Pages

直接将页面根目录选在 main 分支的 docs 目录下即可。
