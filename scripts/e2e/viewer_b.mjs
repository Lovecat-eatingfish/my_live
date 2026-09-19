// 观众B：连接指定房间的 IM，把收到的帧写入文件（GUI 测试的接收端探针）
const roomId = process.argv[2] || '29'
const out = (await import('node:url')).fileURLToPath(new URL('../gui-test-screenshots/viewer_b_received.log', import.meta.url))
const GATEWAY = 'http://localhost:38080/live/api'
async function api(p, q = {}, b = null) {
  const r = await fetch(GATEWAY + p, { method: 'POST', headers: { token: '651b9b92-1737-49a6-a7e5-1d3e37bf9f38' }, body: b ? JSON.stringify(b) : null })
  return r.json()
}
const cfg = await api('/im/getImConfig');
(await import('node:fs')).default.writeFileSync(out, '')
const ws = new WebSocket(`ws://127.0.0.1:38115/131490/1001/${roomId}`)
const fs = (await import('node:fs')).default
ws.onopen = () => fs.appendFileSync(out, `OPEN room=${roomId}\n`)
ws.onmessage = (e) => {
  let m = JSON.parse(e.data)
  if (Array.isArray(m.body)) m.body = Buffer.from(m.body).toString('utf-8')
  fs.appendFileSync(out, `code=${m.code} msgId=${m.msgId || ''} body=${String(m.body).slice(0, 200)}\n`)
}
ws.onclose = (e) => fs.appendFileSync(out, `CLOSE ${e.code}\n`)
// 心跳保活 bind ip 键（与前端一致30s间隔）
setInterval(() => {
  const s = JSON.stringify({ appId: 10001, userId: 131490 })
  if (ws.readyState === 1) ws.send(JSON.stringify({ magic: 19231, code: 1004, len: s.length, body: s }))
}, 25000)
setInterval(() => {}, 1 << 30)
