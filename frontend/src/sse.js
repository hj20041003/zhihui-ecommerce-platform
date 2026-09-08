// SSE 客户端（fetch 流读取）：EventSource 无法自定义 Authorization 头，
// 且令牌不允许放入 URL 查询参数，因此用手动流解析；含 401 刷新与断线重连
import { authHeaders, tryRefresh } from './api.js'

export function sseConnect(path, { onOrders, onAuthFailed }) {
  let stopped = false

  async function loop() {
    while (!stopped) {
      try {
        const resp = await fetch(path, { headers: authHeaders() })
        if (resp.status === 401) {
          if (await tryRefresh()) continue
          if (onAuthFailed) onAuthFailed()
          return
        }
        if (!resp.ok || !resp.body) throw new Error('SSE ' + resp.status)

        const reader = resp.body.getReader()
        const decoder = new TextDecoder()
        let buf = ''
        while (!stopped) {
          const { done, value } = await reader.read()
          if (done) break
          buf += decoder.decode(value, { stream: true })
          let idx
          while ((idx = buf.indexOf('\n\n')) >= 0) {
            const frame = buf.slice(0, idx)
            buf = buf.slice(idx + 2)
            let event = 'message'
            let data = ''
            for (const line of frame.split('\n')) {
              if (line.startsWith('event:')) event = line.slice(6).trim()
              else if (line.startsWith('data:')) data += line.slice(5)
            }
            if (event === 'orders' && data) {
              try { onOrders(JSON.parse(data)) } catch (e) { /* 跳过坏帧 */ }
            }
          }
        }
      } catch (e) { /* 断线，稍后重连 */ }
      if (!stopped) await new Promise(r => setTimeout(r, 3000))
    }
  }

  loop()
  return () => { stopped = true }
}
