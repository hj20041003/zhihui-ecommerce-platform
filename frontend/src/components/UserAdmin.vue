<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['close'])
const users = ref([])
const error = ref('')
const loading = ref(false)
const form = ref({ username: '', password: '', role: 'VIEWER' })

async function load() {
  loading.value = true
  try {
    users.value = await api.listUsers()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  try {
    await api.createUser(form.value)
    form.value = { username: '', password: '', role: 'VIEWER' }
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-head">
        <span>账号管理</span>
        <button class="modal-close" @click="emit('close')">×</button>
      </div>

      <div class="user-table-wrap">
        <table class="user-table">
          <thead>
            <tr><th>用户名</th><th>角色</th><th>状态</th><th>创建时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.username">
              <td>{{ u.username }}</td>
              <td>
                <span class="tag" :class="u.role === 'ADMIN' ? 'paid' : 'pending'">
                  {{ u.role === 'ADMIN' ? '管理员' : '只读' }}
                </span>
              </td>
              <td :style="{ color: u.enabled ? '#00e676' : '#ff4d4f' }">
                {{ u.enabled ? '启用' : '停用' }}
              </td>
              <td class="muted">{{ String(u.created_at).slice(0, 19).replace('T', ' ') }}</td>
            </tr>
            <tr v-if="!users.length && !loading">
              <td colspan="4" class="muted" style="text-align:center;">暂无账号</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="create-form">
        <div class="create-title">创建账号</div>
        <div class="create-row">
          <input v-model="form.username" placeholder="用户名（3-32位字母数字）" />
          <input v-model="form.password" type="password" placeholder="密码（至少6位）" />
          <select v-model="form.role">
            <option value="VIEWER">只读访客</option>
            <option value="ADMIN">管理员</option>
          </select>
          <button @click="submit">创建</button>
        </div>
        <div v-if="error" class="login-error">{{ error }}</div>
      </div>
    </div>
  </div>
</template>
