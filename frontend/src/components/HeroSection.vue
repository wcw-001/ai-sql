<script setup>
defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  statusText: {
    type: String,
    default: ""
  },
  summary: {
    type: Object,
    required: true
  },
  historyCount: {
    type: Number,
    default: 0
  }
});
</script>

<template>
  <header class="masthead">
    <section class="hero">
      <div class="hero-inner">
        <span class="eyebrow">
          <span class="dot"></span>
          Atlas SQL Deck
        </span>
        <h1>自然语言数据驾驶舱</h1>
        <p>
          把模糊的问题翻译成可执行 SQL，并把风险、评分、结果和分页留在同一个工作台里。你可以像分析师一样提问，
          也能像审查员一样看清每一次生成的依据。
        </p>

        <div class="hero-notes">
          <div class="note-card">
            <strong>问题先于语法</strong>
            <span>直接输入业务问题，不必先切换到 SQL 思维；页面会把生成、执行和复核串成一条闭环。</span>
          </div>
          <div class="note-card">
            <strong>风险显性化</strong>
            <span>SQL 评分、风险点和优化建议不再躲在 JSON 中，适合分析和演示时快速复核。</span>
          </div>
          <div class="note-card">
            <strong>结果继续加工</strong>
            <span>结果表支持分页、排序、筛选和导出，适合把一次问答变成一条可落地的数据检查链路。</span>
          </div>
        </div>
      </div>
    </section>

    <aside class="signal">
      <div class="signal-head">
        <h2 class="side-title">会话脉冲</h2>
        <span class="eyebrow">{{ loading ? "RUNNING" : "READY" }}</span>
      </div>
      <div class="side-grid">
        <div class="signal-item">
          <span class="mini-label">当前接口</span>
          <span class="signal-value"><code>/api/query/natural</code></span>
        </div>
        <div class="signal-item">
          <span class="mini-label">最近响应</span>
          <span class="signal-value">{{ summary.durationLabel }}</span>
        </div>
        <div class="signal-item">
          <span class="mini-label">本次会话</span>
          <span class="signal-value">已记录 {{ historyCount }} 次查询，当前质量评分 {{ summary.score }}</span>
        </div>
        <div class="signal-item signal-item-emphasis">
          <span class="mini-label">状态播报</span>
          <span class="signal-value">{{ loading ? "正在生成与执行 SQL..." : statusText }}</span>
        </div>
      </div>
    </aside>
  </header>
</template>
