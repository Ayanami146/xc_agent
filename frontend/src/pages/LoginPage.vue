<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Check, Connection, Lock, Message, Monitor, Phone, User } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { sendSmsCode } from '../services/auth'
import { useAuthStore } from '../stores/auth'
import type { LoginMode } from '../types/auth'
import { apiMode, problemMessage } from '../services/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mode = ref<LoginMode>('password')
const formRef = ref<FormInstance>()
const countdown = ref(0)
let timer = 0

const form = reactive({
  account: apiMode === 'mock' ? '13800138000' : '',
  password: apiMode === 'mock' ? 'XinChuang@2026' : '',
  phone: apiMode === 'mock' ? '13800138000' : '',
  code: '',
  rememberDevice: true,
})
const rules = computed<FormRules>(() => mode.value === 'password' ? {
  account: [{ required: true, message: '请输入手机号或账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }],
} : {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }, { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }],
})

async function requestCode() {
  if (!/^1\d{10}$/.test(form.phone) || countdown.value) { if (!countdown.value) ElMessage.warning('请先输入正确的手机号'); return }
  try { await sendSmsCode(form.phone) } catch (error) { ElMessage.error(problemMessage(error, '验证码发送失败')); return }
  ElMessage.success(apiMode === 'mock' ? '演示验证码已发送，输入任意 6 位数字即可' : '验证码已发送，请注意查收')
  countdown.value = 60
  timer = window.setInterval(() => { countdown.value--; if (!countdown.value) window.clearInterval(timer) }, 1000)
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  try {
    if (mode.value === 'password') await auth.login({ mode: 'password', account: form.account, password: form.password, rememberDevice: form.rememberDevice })
    else await auth.login({ mode: 'sms', phone: form.phone, code: form.code, rememberDevice: form.rememberDevice })
  } catch (error) { ElMessage.error(problemMessage(error, '登录失败')); return }
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/chat'
  await router.replace(redirect)
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <main class="login-page">
    <section class="login-visual" aria-label="产品介绍">
      <div class="visual-grid" />
      <div class="glow glow-one" /><div class="glow glow-two" />
      <div class="orbit orbit-one"><i /><i /><i /></div>
      <div class="orbit orbit-two"><i /><i /></div>
      <div class="visual-content">
        <BrandLogo />
        <div class="visual-copy">
          <span class="visual-eyebrow">ENTERPRISE AI SERVICE</span>
          <h1>让每一次技术咨询<br />都有清晰答案</h1>
          <p>面向国产电脑售后服务场景，融合常见问题、维修手册与智能知识检索，为您提供可靠、可追溯的服务支持。</p>
          <ul>
            <li><span><el-icon><Connection /></el-icon></span><div><strong>智能问题分析</strong><small>结合设备和系统信息给出排查建议</small></div></li>
            <li><span><el-icon><Monitor /></el-icon></span><div><strong>信创知识覆盖</strong><small>支持统信 UOS、银河麒麟及国产整机</small></div></li>
            <li><span><el-icon><Check /></el-icon></span><div><strong>工单持续跟进</strong><small>复杂问题无缝转为留言工单</small></div></li>
          </ul>
        </div>
        <p class="visual-footer">可信 · 专业 · 高效</p>
      </div>
    </section>

    <section class="login-form-side">
      <div class="mobile-brand"><BrandLogo /></div>
      <div class="login-card">
        <div class="login-heading"><span>欢迎使用</span><h2>登录信创智能客服</h2><p>使用您的账号继续访问智能服务</p></div>
        <el-tabs v-model="mode" stretch class="login-tabs" @tab-change="formRef?.clearValidate()">
          <el-tab-pane label="账号密码" name="password" /><el-tab-pane label="手机验证码" name="sms" />
        </el-tabs>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
          <template v-if="mode === 'password'">
            <el-form-item label="手机号或账号" prop="account"><el-input v-model="form.account" :prefix-icon="User" autocomplete="username" placeholder="请输入手机号或账号" /></el-form-item>
            <el-form-item label="密码" prop="password"><el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" placeholder="请输入密码" /></el-form-item>
          </template>
          <template v-else>
            <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" :prefix-icon="Phone" maxlength="11" placeholder="请输入手机号" /></el-form-item>
            <el-form-item label="验证码" prop="code"><div class="code-field"><el-input v-model="form.code" :prefix-icon="Message" maxlength="6" placeholder="6 位验证码" /><el-button :disabled="Boolean(countdown)" @click="requestCode">{{ countdown ? `${countdown}s` : '获取验证码' }}</el-button></div></el-form-item>
          </template>
          <div class="login-options"><el-checkbox v-model="form.rememberDevice">记住此设备</el-checkbox><button type="button">忘记密码？</button></div>
          <el-button class="login-submit" type="primary" native-type="submit" :loading="auth.loading">进入智能客服</el-button>
        </el-form>
        <div v-if="apiMode === 'mock'" class="demo-tip"><span>演示模式</span><p>页面已预填演示信息，点击登录即可体验；不会发送真实验证码。</p></div>
        <p class="agreement">登录即表示您同意《用户服务协议》和《隐私政策》</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: minmax(480px, 1.08fr) minmax(440px, .92fr); background: #f4f8fc; }
.login-visual { min-height: 720px; position: relative; overflow: hidden; color: #fff; background: radial-gradient(circle at 72% 20%, rgba(25,175,222,.34), transparent 30%), linear-gradient(145deg, #071f4a 0%, #0a3e88 56%, #0d63c7 100%); }
.visual-grid { position: absolute; inset: 0; opacity: .22; background-image: linear-gradient(rgba(255,255,255,.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.1) 1px, transparent 1px); background-size: 58px 58px; mask-image: linear-gradient(to bottom right, #000, transparent 75%); }
.glow { position: absolute; border-radius: 50%; filter: blur(5px); background: rgba(0,213,255,.18); animation: drift 10s ease-in-out infinite alternate; }.glow-one { width: 440px; height: 440px; right: -150px; top: 16%; }.glow-two { width: 280px; height: 280px; left: -130px; bottom: 3%; animation-delay: -4s; }
.orbit { position: absolute; border: 1px solid rgba(106,218,255,.27); border-radius: 50%; animation: spin 24s linear infinite; }.orbit-one { width: 430px; height: 430px; right: -110px; top: 20%; }.orbit-two { width: 250px; height: 250px; left: -95px; bottom: 8%; animation-direction: reverse; animation-duration: 18s; }.orbit i { position: absolute; width: 11px; height: 11px; border-radius: 50%; background: #74e7ff; box-shadow: 0 0 18px #6cddff; }.orbit i:nth-child(1) { left: 18%; top: 9%; }.orbit i:nth-child(2) { right: -5px; top: 47%; }.orbit i:nth-child(3) { left: 11%; bottom: 14%; }
.visual-content { min-height: 100%; padding: 42px clamp(42px, 7vw, 108px); position: relative; z-index: 2; display: flex; flex-direction: column; }.visual-content :deep(.brand) { color: #fff; }.visual-content :deep(.brand-mark) { background: rgba(255,255,255,.14); border: 1px solid rgba(255,255,255,.28); }
.visual-copy { margin: auto 0; max-width: 620px; }.visual-eyebrow { color: #70ddf6; font-size: 11px; font-weight: 800; letter-spacing: .24em; }.visual-copy h1 { margin: 15px 0 22px; font-size: clamp(38px, 4vw, 62px); line-height: 1.16; letter-spacing: -.055em; }.visual-copy > p { max-width: 570px; margin: 0; color: rgba(232,244,255,.78); line-height: 1.9; }.visual-copy ul { margin: 38px 0 0; padding: 0; display: grid; gap: 18px; list-style: none; }.visual-copy li { display: flex; gap: 13px; align-items: center; }.visual-copy li > span { width: 42px; height: 42px; border-radius: 13px; display: grid; place-items: center; color: #83ebff; background: rgba(255,255,255,.1); border: 1px solid rgba(255,255,255,.13); }.visual-copy li div { display: flex; flex-direction: column; gap: 4px; }.visual-copy li small { color: rgba(225,240,255,.65); }.visual-footer { color: rgba(255,255,255,.52); font-size: 12px; letter-spacing: .2em; }
.login-form-side { padding: 40px; display: grid; place-items: center; position: relative; background: radial-gradient(circle at 80% 10%, rgba(51,157,255,.09), transparent 28%), #f6f9fc; }.mobile-brand { display: none; }.login-card { width: min(100%, 460px); padding: 42px; border: 1px solid rgba(216,227,238,.92); border-radius: 24px; background: rgba(255,255,255,.9); box-shadow: 0 30px 70px rgba(35,73,111,.13); backdrop-filter: blur(18px); }.login-heading > span { color: var(--primary); font-size: 12px; font-weight: 800; letter-spacing: .12em; }.login-heading h2 { margin: 8px 0 8px; font-size: 29px; color: #14233b; }.login-heading p { margin: 0; color: #8a99ac; }.login-tabs { margin: 24px 0 20px; }.code-field { width: 100%; display: grid; grid-template-columns: 1fr 118px; gap: 10px; }.login-options { margin: -3px 0 18px; display: flex; justify-content: space-between; }.login-options button { border: 0; background: transparent; color: var(--primary); cursor: pointer; }.login-submit { width: 100%; height: 48px; border-radius: 12px; font-weight: 750; box-shadow: 0 10px 24px rgba(23,105,223,.22); }.demo-tip { margin-top: 22px; padding: 12px 14px; border-radius: 12px; background: #f1f7ff; color: #58789e; font-size: 12px; }.demo-tip span { color: var(--primary); font-weight: 800; }.demo-tip p { margin: 4px 0 0; line-height: 1.55; }.agreement { margin: 19px 0 0; text-align: center; color: #a2aebe; font-size: 11px; }
@keyframes drift { to { transform: translate3d(-40px, 24px, 0) scale(1.08); } }@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 980px) { .login-page { grid-template-columns: 1fr; }.login-visual { display: none; }.login-form-side { min-height: 100vh; padding: 28px 20px; align-content: center; gap: 24px; }.mobile-brand { display: block; }.login-card { padding: 32px; } }
@media (max-width: 520px) { .login-card { padding: 26px 20px; border-radius: 20px; }.login-heading h2 { font-size: 25px; }.code-field { grid-template-columns: 1fr 104px; } }
@media (prefers-reduced-motion: reduce) { .glow, .orbit { animation: none; } }
</style>
