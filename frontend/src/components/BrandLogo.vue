<script setup lang="ts">
import { ChatDotRound } from '@element-plus/icons-vue'
defineProps<{ compact?: boolean }>()
</script>

<template>
  <router-link class="brand" to="/chat" aria-label="信创智能客服首页">
    <span class="brand-mark"><el-icon><ChatDotRound /></el-icon></span>
    <span v-if="!compact">信创智能客服</span>
  </router-link>
</template>
