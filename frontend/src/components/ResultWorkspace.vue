<script setup>
import { computed, ref, watch } from "vue";

const props = defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  hasQueried: {
    type: Boolean,
    default: false
  },
  statusTone: {
    type: String,
    default: "idle"
  },
  statusText: {
    type: String,
    default: ""
  },
  pageInfo: {
    type: Object,
    default: null
  },
  pageSizeOptions: {
    type: Array,
    default: () => [10, 20, 50, 100]
  }
});

const emit = defineEmits(["page-change", "page-size-change"]);

const resultView = ref("table");
const resultFilter = ref("");
const sortState = ref({
  column: "",
  direction: "asc"
});

const columns = computed(() => {
  const set = new Set();
  props.rows.forEach((row) => {
    Object.keys(row || {}).forEach((key) => set.add(key));
  });
  return Array.from(set);
});

const filteredRows = computed(() => {
  if (!resultFilter.value) {
    return props.rows;
  }
  const keyword = resultFilter.value.toLowerCase();
  return props.rows.filter((row) => JSON.stringify(row).toLowerCase().includes(keyword));
});

const sortedRows = computed(() => {
  const rows = [...filteredRows.value];
  const { column, direction } = sortState.value;

  if (!column) {
    return rows;
  }

  return rows.sort((left, right) => {
    const a = left?.[column];
    const b = right?.[column];

    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }

    const numberA = Number(a);
    const numberB = Number(b);
    const bothNumbers = Number.isFinite(numberA) && Number.isFinite(numberB);

    const result = bothNumbers
      ? numberA - numberB
      : String(a).localeCompare(String(b), "zh-CN", { numeric: true, sensitivity: "base" });

    return direction === "asc" ? result : -result;
  });
});

const jsonRows = computed(() => JSON.stringify(sortedRows.value, null, 2));
const columnCount = computed(() => columns.value.length);
const visibleRows = computed(() => sortedRows.value.length);
const selectedPageSize = computed({
  get: () => String(props.pageInfo?.pageSize || props.pageSizeOptions[0]),
  set: (value) => emit("page-size-change", Number(value))
});
const pageSummary = computed(() => {
  if (!props.pageInfo) {
    return "未分页";
  }
  const totalPages = Math.max(props.pageInfo.totalPages || 0, 1);
  return `第 ${props.pageInfo.page} / ${totalPages} 页，共 ${props.pageInfo.total} 条`;
});
const emptyState = computed(() => {
  if (props.loading) {
    return {
      title: "正在生成查询结果",
      description: "系统正在理解问题、生成 SQL 并执行请求，结果会自动刷新到这里。"
    };
  }
  if (!props.hasQueried) {
    return {
      title: "还没有结果数据",
      description: "先输入一个业务问题，比如“查询所有用户”，让工作台开始第一次响应。"
    };
  }
  if (props.statusTone === "error") {
    return {
      title: "本次查询没有得到结果",
      description: props.statusText || "请求执行失败，请调整问题描述或稍后重试。"
    };
  }
  return {
    title: "查询已执行但没有记录",
    description: "可以尝试放宽筛选条件、扩大时间范围，或检查关联字段是否合理。"
  };
});
const filterEmpty = computed(() => props.rows.length > 0 && sortedRows.value.length === 0);

watch(columns, (nextColumns) => {
  if (!nextColumns.includes(sortState.value.column)) {
    sortState.value = { column: "", direction: "asc" };
  }
});

function formatCell(value) {
  if (value == null) {
    return "";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function toggleSort(column) {
  if (sortState.value.column !== column) {
    sortState.value = { column, direction: "asc" };
    return;
  }

  if (sortState.value.direction === "asc") {
    sortState.value = { column, direction: "desc" };
    return;
  }

  sortState.value = { column: "", direction: "asc" };
}

function sortIndicator(column) {
  if (sortState.value.column !== column) {
    return "↕";
  }
  return sortState.value.direction === "asc" ? "↑" : "↓";
}

function downloadFile(content, type, fileName) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function exportCsv() {
  if (!sortedRows.value.length) {
    return;
  }

  const header = columns.value;
  const csv = [
    header.join(","),
    ...sortedRows.value.map((row) =>
      header
        .map((column) => {
          const value = formatCell(row[column]).replaceAll('"', '""');
          return `"${value}"`;
        })
        .join(",")
    )
  ].join("\n");

  downloadFile(csv, "text/csv;charset=utf-8", "query-result.csv");
}

function exportJson() {
  if (!sortedRows.value.length) {
    return;
  }
  downloadFile(jsonRows.value, "application/json;charset=utf-8", "query-result.json");
}
</script>

<template>
  <section class="panel panel-results">
    <div class="result-top">
      <div>
        <h2 class="section-title">结果工作区</h2>
        <p class="section-desc">在表格和 JSON 两种视图之间切换，继续排序、筛选、导出或翻页复查。</p>
      </div>
      <div class="action-group">
        <button
          type="button"
          class="table-toggle"
          :class="{ active: resultView === 'table' }"
          @click="resultView = 'table'"
        >
          表格视图
        </button>
        <button
          type="button"
          class="table-toggle"
          :class="{ active: resultView === 'json' }"
          @click="resultView = 'json'"
        >
          JSON 视图
        </button>
      </div>
    </div>

    <div class="result-meta-grid">
      <article class="mini-stat-card">
        <span class="metric-label">当前行数</span>
        <strong>{{ visibleRows }}</strong>
      </article>
      <article class="mini-stat-card">
        <span class="metric-label">字段数量</span>
        <strong>{{ columnCount }}</strong>
      </article>
      <article class="mini-stat-card">
        <span class="metric-label">排序状态</span>
        <strong>{{ sortState.column ? `${sortState.column} ${sortState.direction}` : "未排序" }}</strong>
      </article>
      <article class="mini-stat-card">
        <span class="metric-label">分页概览</span>
        <strong>{{ pageSummary }}</strong>
      </article>
    </div>

    <div class="table-toolbar">
      <input
        v-model.trim="resultFilter"
        class="table-search"
        type="text"
        placeholder="筛选当前页结果中的关键词"
      />

      <div class="toolbar-group">
        <label class="page-size-group">
          <span class="mini-label">每页</span>
          <select v-model="selectedPageSize" class="page-size-select" :disabled="loading">
            <option v-for="size in pageSizeOptions" :key="size" :value="String(size)">{{ size }}</option>
          </select>
        </label>

        <div class="action-group">
          <button type="button" class="mini-action" :disabled="!sortedRows.length" @click="exportCsv">
            导出 CSV
          </button>
          <button type="button" class="mini-action" :disabled="!sortedRows.length" @click="exportJson">
            导出 JSON
          </button>
        </div>
      </div>
    </div>

    <div v-if="!rows.length" class="empty-state" :class="statusTone">
      <strong>{{ emptyState.title }}</strong>
      <span>{{ emptyState.description }}</span>
    </div>

    <div v-else-if="filterEmpty" class="empty-state ok">
      <strong>筛选后没有匹配结果</strong>
      <span>尝试更换关键词，或者切回 JSON 视图检查字段内容。</span>
    </div>

    <div v-else-if="resultView === 'table'" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th
              v-for="column in columns"
              :key="column"
              class="sortable"
              @click="toggleSort(column)"
            >
              <span>{{ column }}</span>
              <span class="sort-indicator">{{ sortIndicator(column) }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in sortedRows" :key="rowIndex">
            <td v-for="column in columns" :key="column">{{ formatCell(row[column]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <pre v-else class="json-view">{{ jsonRows }}</pre>

    <div v-if="pageInfo" class="pagination-bar">
      <div class="pager-summary">
        <span>{{ pageSummary }}</span>
        <span>当前页显示 {{ rows.length }} 条</span>
      </div>
      <div class="pager-group">
        <button
          type="button"
          class="mini-action"
          :disabled="loading || !pageInfo.hasPrevious"
          @click="emit('page-change', pageInfo.page - 1)"
        >
          上一页
        </button>
        <button
          type="button"
          class="mini-action"
          :disabled="loading || !pageInfo.hasNext"
          @click="emit('page-change', pageInfo.page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </section>
</template>
