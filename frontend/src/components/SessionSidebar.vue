<script setup>
defineProps({
  promptPresets: {
    type: Array,
    default: () => []
  },
  summary: {
    type: Object,
    required: true
  },
  history: {
    type: Array,
    default: () => []
  }
});

defineEmits(["apply-preset", "restore-history"]);
</script>

<template>
  <div class="aside-stack">
    <div class="aside-card">
      <h3 class="card-title">高频问题模板</h3>
      <p class="card-copy">适合演示、联调和快速验证，让你更快进入正确的提问粒度。</p>
      <div class="chip-row">
        <button
          v-for="item in promptPresets"
          :key="item"
          class="chip"
          type="button"
          @click="$emit('apply-preset', item)"
        >
          {{ item }}
        </button>
      </div>
    </div>

    <div class="aside-card">
      <h3 class="card-title">本次会话快照</h3>
      <div class="stat-grid">
        <div class="stat-tile">
          <span class="metric-label">最近耗时</span>
          <span class="metric-value">{{ summary.durationLabel }}</span>
        </div>
        <div class="stat-tile">
          <span class="metric-label">结果行数</span>
          <span class="metric-value">{{ summary.rows }}</span>
        </div>
        <div class="stat-tile">
          <span class="metric-label">SQL 评分</span>
          <span class="metric-value">{{ summary.score }}</span>
        </div>
        <div class="stat-tile">
          <span class="metric-label">评级</span>
          <span class="metric-value">{{ summary.grade }}</span>
        </div>
      </div>
    </div>

    <div class="aside-card">
      <h3 class="card-title">最近查询</h3>
      <p class="card-copy">保留当前会话中的关键提问，便于回放同一条分析链路。</p>
      <div v-if="history.length" class="history-list">
        <button
          v-for="item in history"
          :key="item.id"
          type="button"
          class="history-item"
          @click="$emit('restore-history', item)"
        >
          <strong>{{ item.query }}</strong>
          <span>{{ item.meta }}</span>
        </button>
      </div>
      <div v-else class="empty">还没有历史记录，先执行一次查询吧。</div>
    </div>
  </div>
</template>
