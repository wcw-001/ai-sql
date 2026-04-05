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
  statusTone: {
    type: String,
    default: "idle"
  },
  summary: {
    type: Object,
    required: true
  }
});
</script>

<template>
  <section class="panel summary-panel">
    <div class="summary-grid">
      <article class="summary-card highlight">
        <span class="metric-label">状态</span>
        <span class="summary-value">{{ loading ? "RUN" : statusTone.toUpperCase() }}</span>
        <div class="summary-desc">{{ statusText }}</div>
      </article>
      <article class="summary-card sea">
        <span class="metric-label">Rows</span>
        <span class="summary-value">{{ summary.rows }}</span>
        <div class="summary-desc">当前结果集中可直接浏览的记录数</div>
      </article>
      <article class="summary-card gold">
        <span class="metric-label">Score</span>
        <span class="summary-value">{{ summary.score }}</span>
        <div class="summary-desc">综合了风险提示与结构质量的评分</div>
      </article>
      <article class="summary-card">
        <span class="metric-label">Latency</span>
        <span class="summary-value">{{ summary.durationLabel }}</span>
        <div class="summary-desc">浏览器侧观测到的响应耗时</div>
      </article>
    </div>
    <div class="status-bar" :class="statusTone">{{ statusText }}</div>
  </section>
</template>
