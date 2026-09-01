<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Document, QuestionFilled, Search } from '@element-plus/icons-vue'
import { getFaqDetail, getManualDownloadUrl, listKnowledge } from '../services/content'
import type { FaqDetail, KnowledgeItem, KnowledgeKind } from '../types/content'
import { ElMessage } from 'element-plus'
import { problemMessage } from '../services/http'

const emit = defineEmits<{ ask: [question: string]; selected: [] }>()
const activeKind = ref<KnowledgeKind>('FAQ')
const keyword = ref('')
const items = ref<KnowledgeItem[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FaqDetail | null>(null)
let debounce = 0

const label = computed(() => activeKind.value === 'FAQ' ? '热门 FAQ' : '维修手册')
async function load() { loading.value = true; try { items.value = await listKnowledge(activeKind.value, keyword.value) } catch (error) { items.value = []; ElMessage.error(problemMessage(error, '知识内容加载失败')) } finally { loading.value = false } }
async function select(item: KnowledgeItem) {
  if (item.kind === 'FAQ') {
    detailLoading.value = true
    detailVisible.value = true
    try {
      detail.value = await getFaqDetail(item.id)
    } catch (error) {
      detailVisible.value = false
      detail.value = null
      ElMessage.error(problemMessage(error, 'FAQ 详情加载失败'))
    } finally {
      detailLoading.value = false
    }
    return
  } else {
    try {
      const grant = await getManualDownloadUrl(item.id)
      window.open(grant.url, '_blank', 'noopener,noreferrer')
    } catch (error) { ElMessage.error(problemMessage(error, '手册下载失败')) }
  }
  emit('selected')
}
function continueQuestion() {
  if (!detail.value) return
  emit('ask', detail.value.question)
  detailVisible.value = false
  emit('selected')
}
watch([activeKind, keyword], () => { clearTimeout(debounce); debounce = window.setTimeout(load, 180) })
onMounted(load)
</script>

<template>
  <aside class="panel knowledge-panel">
    <label class="search-field knowledge-search"><el-icon><Search /></el-icon><input v-model="keyword" placeholder="搜索 FAQ / 维修手册" /></label>
    <div class="tabs"><button :class="{ active: activeKind === 'FAQ' }" @click="activeKind = 'FAQ'">热门 FAQ</button><button :class="{ active: activeKind === 'MANUAL' }" @click="activeKind = 'MANUAL'">维修手册</button></div>
    <div class="knowledge-header"><strong>{{ label }}</strong><span>{{ items.length }} 条</span></div>
    <div v-loading="loading" class="knowledge-list">
      <button v-for="item in items" :key="item.id" class="knowledge-item" @click="select(item)">
        <span class="knowledge-dot"><el-icon><component :is="item.kind === 'FAQ' ? QuestionFilled : Document" /></el-icon></span>
        <span class="knowledge-copy"><strong>{{ item.title }}</strong><small>{{ item.summary }}</small></span>
        <span class="knowledge-meta"><time>{{ item.updatedAt }}</time><em v-if="item.hotCount">◆ {{ item.hotCount }}</em></span>
      </button>
      <div v-if="!loading && !items.length" class="knowledge-empty">没有找到相关内容</div>
    </div>
    <button class="view-more">查看全部知识内容 <span>→</span></button>
  </aside>
  <el-drawer v-model="detailVisible" title="FAQ 详情" size="min(560px, 94vw)" append-to-body>
    <div v-loading="detailLoading" class="faq-detail">
      <template v-if="detail">
        <span class="eyebrow">{{ detail.categoryName }}</span>
        <h2>{{ detail.title }}</h2>
        <h3>{{ detail.question }}</h3>
        <p class="faq-answer">{{ detail.answer }}</p>
        <p v-if="detail.summary" class="faq-summary">{{ detail.summary }}</p>
        <el-button type="primary" size="large" class="continue-button" @click="continueQuestion">
          继续向智能体提问
        </el-button>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.knowledge-header { margin: 16px 2px 2px; display: flex; justify-content: space-between; color: #3c506c; font-size: 13px; }
.knowledge-header span { color: #9aa8b8; }
.knowledge-dot { font-size: 12px; }
.knowledge-empty { padding: 38px 0; text-align: center; color: #9aa8b8; font-size: 13px; }
.faq-detail { min-height: 220px; padding: 4px 12px 24px; }
.faq-detail h2 { margin: 10px 0 20px; color: #203a5f; }
.faq-detail h3 { margin: 0 0 12px; color: #35577f; font-size: 16px; line-height: 1.6; }
.faq-answer { margin: 0; padding: 18px; border-radius: 12px; color: #344d6b; background: #f4f8fc; line-height: 1.8; white-space: pre-wrap; }
.faq-summary { color: #8291a5; line-height: 1.6; }
.continue-button { width: 100%; margin-top: 22px; }
</style>
