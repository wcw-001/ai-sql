<script setup>
import { computed, ref } from "vue";

const props = defineProps({
  sql: {
    type: String,
    default: ""
  },
  analysis: {
    type: Object,
    default: null
  },
  summary: {
    type: Object,
    required: true
  },
  statusTone: {
    type: String,
    default: "idle"
  }
});

const copyState = ref("idle");

const sqlDisplay = computed(() => props.sql || "-- 暂无 SQL 输出");
const analysisRisks = computed(() => props.analysis?.risks?.length ? props.analysis.risks : ["暂无风险信息"]);
const analysisSuggestions = computed(() => props.analysis?.suggestions?.length ? props.analysis.suggestions : ["暂无优化建议"]);
const analysisGradeLabel = computed(() => props.analysis?.level ? `评级 ${props.analysis.level}` : "暂无评级");
const gradeClass = computed(() => {
  const level = (props.analysis?.level || "").toLowerCase();
  return level ? `grade-${level}` : "";
});
const sqlMeta = computed(() => ({
  lines: props.sql ? props.sql.split("\n").length : 0,
  length: props.sql ? props.sql.length : 0
}));

const copyButtonLabel = computed(() => {
  if (copyState.value === "done") {
    return "已复制";
  }
  if (copyState.value === "error") {
    return "复制失败";
  }
  return "复制 SQL";
});

async function copySql() {
  if (!props.sql) {
    return;
  }

  try {
    await navigator.clipboard.writeText(props.sql);
    copyState.value = "done";
  } catch {
    copyState.value = "error";
  }

  window.clearTimeout(copySql.timer);
  copySql.timer = window.setTimeout(() => {
    copyState.value = "idle";
  }, 1600);
}
</script>

<template>
  <article class="analysis-stack">
    <section class="code-card">
      <div class="inspector-head">
        <div>
          <h2 class="section-title">生成 SQL</h2>
          <p class="section-desc">保留 SQL 的结构可读性，方便复制、审查或继续手工优化。</p>
        </div>
        <div class="action-group">
          <button type="button" class="mini-action" :disabled="!sql" @click="copySql">
            {{ copyButtonLabel }}
          </button>
        </div>
      </div>

      <div class="meta-strip">
        <span class="meta-pill">状态 {{ statusTone.toUpperCase() }}</span>
        <span class="meta-pill">行数 {{ sqlMeta.lines }}</span>
        <span class="meta-pill">字符 {{ sqlMeta.length }}</span>
      </div>

      <pre class="code-block">{{ sqlDisplay }}</pre>
    </section>

    <section class="analysis-card">
      <h2 class="section-title">质量解读</h2>
      <p class="section-desc">把 SQL 风险和优化建议拆开呈现，便于快速判断是否需要继续修正。</p>
      <div class="score-tags">
        <span class="tag" :class="gradeClass">{{ analysisGradeLabel }}</span>
        <span class="tag">评分 {{ summary.score }}</span>
        <span class="tag">结果 {{ summary.rows }} 行</span>
      </div>
      <div class="risk-columns">
        <section class="risk-panel">
          <h3>风险观察</h3>
          <ul class="risk-list">
            <li v-for="item in analysisRisks" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="risk-panel">
          <h3>优化建议</h3>
          <ul class="risk-list">
            <li v-for="item in analysisSuggestions" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>
    </section>
  </article>
</template>
