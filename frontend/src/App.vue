<script setup>
import { computed, ref } from "vue";
import HeroSection from "./components/HeroSection.vue";
import QueryComposer from "./components/QueryComposer.vue";
import SessionSidebar from "./components/SessionSidebar.vue";
import SqlInspector from "./components/SqlInspector.vue";
import StatusSummary from "./components/StatusSummary.vue";
import ResultWorkspace from "./components/ResultWorkspace.vue";

const DEFAULT_PAGE_SIZE = 20;
const pageSizeOptions = [10, 20, 50, 100];
const promptPresets = [
  "查询所有用户",
  "查询最近 7 天注册的用户数量，按天分组",
  "查询订单金额最高的前 10 个用户",
  "统计每个订单状态下的订单数量"
];

const query = ref("");
const loading = ref(false);
const hasQueried = ref(false);
const statusText = ref("控制台已就绪，输入自然语言即可开始分析。");
const statusTone = ref("idle");
const sql = ref("");
const rows = ref([]);
const analysis = ref(null);
const durationMs = ref(null);
const history = ref([]);
const pageInfo = ref(null);

const summary = computed(() => ({
  rows: rows.value.length,
  score: typeof analysis.value?.score === "number" ? analysis.value.score : "--",
  grade: analysis.value?.level || "--",
  durationLabel: durationMs.value == null ? "--" : `${durationMs.value} ms`
}));

function applyPreset(text) {
  query.value = text;
}

function useRandomPrompt() {
  const next = promptPresets[Math.floor(Math.random() * promptPresets.length)];
  applyPreset(next);
}

function clearWorkspace() {
  query.value = "";
  sql.value = "";
  rows.value = [];
  analysis.value = null;
  durationMs.value = null;
  hasQueried.value = false;
  pageInfo.value = null;
  statusText.value = "画布已清空，可以重新发起新的数据问题。";
  statusTone.value = "idle";
}

function normalizeResponsePayload(payload) {
  const data = payload?.data || {};
  sql.value = data.sql || "";
  rows.value = Array.isArray(data.data) ? data.data : [];
  analysis.value = data.sqlAnalysis || null;
  pageInfo.value = data.pageInfo || null;
}

function pushHistory() {
  const trimmed = query.value.trim();
  if (!trimmed) {
    return;
  }

  const pageMeta = pageInfo.value
    ? `第 ${pageInfo.value.page}/${Math.max(pageInfo.value.totalPages || 1, 1)} 页`
    : "未分页";

  const nextItem = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    query: trimmed,
    sql: sql.value,
    data: rows.value,
    sqlAnalysis: analysis.value,
    pageInfo: pageInfo.value,
    meta: `${summary.value.rows} 行 · ${summary.value.score} 分 · ${pageMeta}`
  };

  history.value = [nextItem, ...history.value.filter((item) => item.query !== nextItem.query)].slice(0, 6);
}

function restoreHistory(item) {
  query.value = item.query;
  sql.value = item.sql || "";
  rows.value = Array.isArray(item.data) ? item.data : [];
  analysis.value = item.sqlAnalysis || null;
  pageInfo.value = item.pageInfo || null;
  hasQueried.value = true;
  statusText.value = `已恢复查询：${item.query}`;
  statusTone.value = "idle";
}

async function fetchQueryResult(targetPage = 1, targetPageSize = DEFAULT_PAGE_SIZE, writeHistory = false) {
  const trimmed = query.value.trim();
  if (!trimmed) {
    statusText.value = "请先输入查询意图，再执行 SQL 生成。";
    statusTone.value = "error";
    return;
  }

  hasQueried.value = true;
  loading.value = true;
  statusText.value = "正在理解问题、生成 SQL 并请求结果...";
  statusTone.value = "idle";

  const start = performance.now();

  try {
    const response = await fetch(
      `/api/query/natural?q=${encodeURIComponent(trimmed)}&page=${targetPage}&pageSize=${targetPageSize}`
    );
    const payload = await response.json();
    durationMs.value = Math.round(performance.now() - start);

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    if (payload.code !== 0 || !payload.data) {
      sql.value = "";
      rows.value = [];
      analysis.value = null;
      pageInfo.value = null;
      statusText.value = `执行失败：${payload.message || "未知错误"}`;
      statusTone.value = "error";
      return;
    }

    normalizeResponsePayload(payload);
    statusTone.value = "ok";
    statusText.value = rows.value.length
      ? `查询完成，当前页返回 ${rows.value.length} 条记录。`
      : "查询已执行，但当前页没有返回记录。";

    if (writeHistory) {
      pushHistory();
    }
  } catch (error) {
    durationMs.value = Math.round(performance.now() - start);
    sql.value = "";
    rows.value = [];
    analysis.value = null;
    pageInfo.value = null;
    statusText.value = `请求异常：${error.message}`;
    statusTone.value = "error";
  } finally {
    loading.value = false;
  }
}

function runQuery() {
  const currentPageSize = pageInfo.value?.pageSize || DEFAULT_PAGE_SIZE;
  fetchQueryResult(1, currentPageSize, true);
}

function changePage(nextPage) {
  const currentPageSize = pageInfo.value?.pageSize || DEFAULT_PAGE_SIZE;
  fetchQueryResult(nextPage, currentPageSize, false);
}

function changePageSize(nextPageSize) {
  fetchQueryResult(1, nextPageSize, false);
}
</script>

<template>
  <div class="shell">
    <HeroSection
      :loading="loading"
      :status-text="statusText"
      :summary="summary"
      :history-count="history.length"
    />

    <main class="workspace">
      <section class="panel panel-stage">
        <div class="query-grid">
          <QueryComposer
            v-model="query"
            :loading="loading"
            @run="runQuery"
            @random="useRandomPrompt"
            @clear="clearWorkspace"
          />

          <SessionSidebar
            :prompt-presets="promptPresets"
            :summary="summary"
            :history="history"
            @apply-preset="applyPreset"
            @restore-history="restoreHistory"
          />
        </div>
      </section>

      <StatusSummary
        :loading="loading"
        :status-text="statusText"
        :status-tone="statusTone"
        :summary="summary"
      />

      <section class="result-grid">
        <SqlInspector
          :sql="sql"
          :analysis="analysis"
          :summary="summary"
          :status-tone="statusTone"
        />

        <ResultWorkspace
          :rows="rows"
          :loading="loading"
          :has-queried="hasQueried"
          :status-tone="statusTone"
          :status-text="statusText"
          :page-info="pageInfo"
          :page-size-options="pageSizeOptions"
          @page-change="changePage"
          @page-size-change="changePageSize"
        />
      </section>
    </main>
  </div>
</template>
