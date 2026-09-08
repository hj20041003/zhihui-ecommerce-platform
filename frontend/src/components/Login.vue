<script setup>
import { ref } from 'vue'
import { login } from '../api.js'

const emit = defineEmits(['success'])
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const user = await login(username.value.trim(), password.value)
    emit('success', user)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-mask">
    <form class="login-card" @submit.prevent="submit">
      <div class="login-logo"></div>
      <h1 class="login-title">智辉电商全域运营数据分析平台</h1>
      <p class="login-sub">请使用平台账号登录 · 内部系统不开放自助注册</p>

      <label class="login-field">
        <span>用户名</span>
        <input v-model="username" type="text" autocomplete="username" placeholder="请输入用户名" />
      </label>
      <label class="login-field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
      </label>

      <div v-if="error" class="login-error">{{ error }}</div>

      <button class="login-btn" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登 录' }}
      </button>

      <p class="login-hint">演示账号：admin / admin123（管理员）　viewer / viewer123（只读）</p>
    </form>
  </div>
</template>
