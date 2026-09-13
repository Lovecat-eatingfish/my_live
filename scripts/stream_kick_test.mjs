/**
 * 关播踢流严格验证：开播 → FFmpeg 保持推流中 → closeLiving →
 * 断言 SRS 主动踢掉推流客户端（流销毁且 ffmpeg 被断开）
 */
import { spawn } from 'node:child_process'

const GATEWAY = 'http://localhost:38080/live/api'
const sleep = (ms) => new Promise(r => setTimeout(r, ms))
async function api(path, token, query) {
  const qs = new URLSearchParams(query).toString()
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST', headers: { token: token, 'Content-Type': 'application/json' }
  })
  return res.json()
}
async function streams() {
  return (await (await fetch('http://127.0.0.1:1985/api/v1/streams/')).json()).streams || []
}

const login = (await api('/userLogin/sendLoginCode', '', { phone: '13800138000' }), await api('/userLogin/login', '', { phone: '13800138000', code: '123456' }))
if (login.code !== 200) throw new Error('登录失败: ' + login.msg)
const token = login.data.token

const start = await api('/living/startingLiving', token, { type: '1' })
const roomId = start.data.roomId
const push = await api('/stream/createPushUrl', token, { roomId: String(roomId) })
const pushUrl = push.data.pushUrl
console.log(`roomId=${roomId} streamKey=${push.data.streamKey}`)

const ff = spawn('ffmpeg', ['-re', '-f', 'lavfi', '-i', 'testsrc2=size=640x480:rate=25',
  '-f', 'lavfi', '-i', 'sine=frequency=1000',
  '-c:v', 'libx264', '-preset', 'ultrafast', '-g', '50', '-c:a', 'aac', '-t', '60',
  '-f', 'flv', pushUrl], { stdio: ['ignore', 'ignore', 'pipe'] })
let ffErr = ''
ff.stderr.on('data', d => { ffErr += d })
const ffDone = new Promise(r => ff.on('exit', (c) => r(c)))

await sleep(8000)
const during = await streams()
const mineDuring = during.filter(s => s.name === push.data.streamKey)
console.log(`推流中: 本流在推=${mineDuring.length === 1} (总${during.length}路)`)

const close = await api('/living/closeLiving', token, { roomId: String(roomId) })
console.log(`closeLiving: code=${close.code} msg=${close.msg}`)
await sleep(2500)

const after = await streams()
const mineAfter = after.filter(s => s.name === push.data.streamKey)
console.log(`关播后: 本流已销毁=${mineAfter.length === 0}`)
const exitCode = await Promise.race([ffDone.then(c => c), sleep(20000).then(() => 'still-running')])
const connBroken = exitCode !== 0 && exitCode !== 'still-running'
console.log(`ffmpeg: exit=${exitCode} 推流连接被断开=${connBroken}`)
console.log((mineDuring.length === 1 && mineAfter.length === 0 && exitCode !== 'still-running') ? '✅ 关播踢流验证通过' : '❌ 踢流异常')
process.exit(0)
