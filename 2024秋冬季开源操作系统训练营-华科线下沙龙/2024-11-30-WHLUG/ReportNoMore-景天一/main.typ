#import "@preview/cuti:0.2.1": show-cn-fakebold
#show: show-cn-fakebold

#import "@preview/codelst:2.0.0": sourcecode


#set math.equation(numbering: "1-1")
#set math.equation(supplement: "公式")

#show figure.where(kind: image): set figure(supplement: [图])

// #let huawenkaiti = ("Times New Roman", "STKaiti")
// #let heiti = ("Times New Roman", "Heiti SC", "Heiti TC", "SimHei")
// #let songti = ("Times New Roman", "Songti SC", "Songti TC", "SimSun")
// #let zhongsong = ("STZhongsong", "Times New Roman")

#let heiti = ("Times New Roman", "Heiti SC", "Heiti TC")
#let songti = ("Times New Roman", "Songti SC", "Songti TC")

#import "@preview/touying:0.5.3": *
#import themes.university: *
#import "@preview/numbly:0.1.0": numbly

#show: university-theme.with(
  aspect-ratio: "16-9",
  // config-common(handout: true),
  config-info(
    title: [再见报告],
    subtitle: [不使用MS Word撰写本科和研究生实验报告],
    author: [Felix Jing],
    // date: datetime.today(),
    date: [2024-11-30],
    // institution: [Institution],
    // logo: emoji.school,
  ),
)

#set heading(numbering: numbly("{1}.", default: "1.1"))
#set text(font: songti)

#title-slide()

== Outline <touying:hidden>

#components.adaptive-columns(outline(title: none, indent: 1em, depth: 2))

= Status Quo

== 报告太多了!

#grid(
  columns: (auto, auto, auto),
  image("./assets/reports.png", width: 100%),
  image("./assets/reports-2.png", width: 100%),
  image("./assets/reports-3.png", width: 120%),
)

= 再见了, 所有的实验报告

== 用Markdown写本科生课程报告


#text(size: 20pt)[

  用 Markdown 写报告，Pandoc映射为Word

  https://github.com/woolen-sheep/md2report

  #align(center)[
    #figure(
      image("./assets/front_page.png", width: 70%),
    ) <front_page>

  ]
]

== But, Markdown 的局限性

难以支持更多的自定义语法，需要更复杂的解析器

如：自动生成引用，自动引用图片（如@front_page ），表格和公式

#pause

#align(center)[

  #text(size: 40pt)[

    *需要更好的解决方案*

  ]
]

= Typst: 更好的解决方案

== Typst /taɪpst/

LaTex? Typst!

高定制化的模板，同时具有简单的语法（没有比Markdown复杂多少）

#sourcecode[```typ
  $ 2^L = { S | S in I } $ <eq10>
  ```]

$ 2^L = { S | S in I } $ <eq10>

自动引用图片 _@front_page _，表格和公式 _@eq10 _

完整的 LSP 支持：_Tinymist_

== HUST-typst-template

#text(size: 20pt)[像素级复刻Word模板，支持多级标题，目录，参考文献 .etc]

#align(center)[

  #grid(
    columns: (auto, auto, auto),
    image("./assets/paper.png", width: 100%),
    image("./assets/report-typst.png", width: 100%),
    image("./assets/ref.png", width: 100%),
  )

]
== PPT, You Name It

#align(center)[

  *_Touying_* is a powerful package for creating presentation slides in Typst.

  #figure(
    image("./assets/ppt.png", width: 71%),
  )
]

= Thanks
