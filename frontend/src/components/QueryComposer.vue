<script setup>
const props = defineProps({
  modelValue: {
    type: String,
    default: ""
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["update:modelValue", "run", "random", "clear"]);

function handleShortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    emit("run");
  }
}
</script>

<template>
  <div>
    <h2 class="section-title">查询编排台</h2>
    <p class="section-desc">
      把时间范围、对象、指标和排序方式说清楚，模型更容易命中正确表、字段和过滤条件。
    </p>

    <div class="editor-shell">
      <div class="editor-meta">
        <span class="mini-label">Natural language prompt</span>
        <span class="char-counter">{{ props.modelValue.trim().length }} 字</span>
      </div>

      <textarea
        :value="props.modelValue"
        placeholder="示例：查询最近 7 天注册用户数，按天统计，并返回日期、用户数，按日期升序排列。"
        @input="emit('update:modelValue', $event.target.value)"
        @keydown="handleShortcut"
      ></textarea>

      <div class="composer-hints">
        <span class="hint-pill">明确时间范围</span>
        <span class="hint-pill">说明聚合口径</span>
        <span class="hint-pill">指出排序与分页关注点</span>
      </div>

      <div class="action-row">
        <div class="action-group">
          <button class="action-btn primary" type="button" :disabled="props.loading" @click="emit('run')">
            <template v-if="props.loading">
              <span class="loading-dots" aria-hidden="true"><span></span><span></span><span></span></span>
              执行中
            </template>
            <template v-else>执行查询</template>
          </button>
          <button class="action-btn secondary" type="button" :disabled="props.loading" @click="emit('random')">
            填入示例
          </button>
          <button class="action-btn ghost" type="button" :disabled="props.loading" @click="emit('clear')">
            清空画布
          </button>
        </div>
        <span class="hint">快捷键：`Ctrl/Cmd + Enter` 可直接执行。</span>
      </div>
    </div>
  </div>
</template>
