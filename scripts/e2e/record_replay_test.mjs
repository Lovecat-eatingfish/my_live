/**
 * 批次十二 E2E：录制回放闭环
 * 1 开播携带 recordEnabled=1  2 ffmpeg 真实推流 12s  3 SRS on_dvr → 上传 MinIO → 回放记录落库
 * 4 /stream/records 可见  5 /stream/recordsByAnchor（主播主页回放）可见
 * 用法: node scripts/e2e/record_replay_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const { spawn } = await import('node:child_process')

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {} } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST', headers: { 'token': token, 'Content-Type': 'application/json' }
  }).then(r => r.json())
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

const run = async () => {
  const B = await login('13876543211')
  log('login', true, `B=${B.userId}`)

  // ---- 1. 开播开启录制 ----
  let room = await api('/living/startingLiving', B.token,
    { query: { type: 1, roomName: '录制回放测试间', covertImg: 'x', recordEnabled: 1 } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token,
      { query: { type: 1, roomName: '录制回放测试间', covertImg: 'x', recordEnabled: 1 } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('开播(录制开启)', true, `roomId=${roomId}`)

  // ---- 2. 真实推流 12s（-re） ----
  let pushUrl = null
  for (let i = 0; i < 5; i++) {
    const push = await api('/stream/createPushUrl', B.token, { query: { roomId } })
    pushUrl = typeof push.data === 'string' ? push.data : push.data?.pushUrl
    if (pushUrl) break
    await sleep(3000) // Dubbo 冷连接，stream-provider 刚重启需要轮询
  }
  log('拿推流地址', !!pushUrl, pushUrl)
  const ff = spawn('ffmpeg', ['-re', '-f', 'lavfi', '-i', 'testsrc=duration=12:size=640x360:rate=20',
    '-f', 'lavfi', '-i', 'sine=duration=12', '-c:v', 'libx264', '-preset', 'veryfast',
    '-c:a', 'aac', '-f', 'flv', pushUrl], { stdio: 'ignore' })
  await sleep(15000)
  try { ff.kill() } catch {}
  log('推流12s完成', true)

  // ---- 3. 停流 → on_unpublish → SRS 关闭 DVR → on_dvr 回调 ----
  // SRS session 计划在流结束时收尾 dvr 文件；等待上传+落库（给 40s）
  let record = null
  for (let i = 0; i < 20; i++) {
    await sleep(2000)
    const vo = await api('/stream/records', B.token, { query: { roomId } })
    record = (vo.data || []).find(r => r.roomId === roomId)
    if (record) break
  }
  log('回放记录落库', !!record, record ? `url=${record.recordUrl?.slice(0, 70)}... 时长=${record.duration}s` : '未出现(40s 内)')

  // ---- 4. 回放文件可访问（MinIO 公网 URL） ----
  if (record) {
    // recordUrl 是给浏览器用的公网地址（走 vite/nginx 代理）；本机直连 MinIO 兜底校验
    let head
    try {
      head = await fetch(record.recordUrl, { method: 'GET' })
    } catch {
      const path = new URL(record.recordUrl).pathname.replace(/^\/minio/, '')
      head = await fetch(`http://127.0.0.1:9000${path}`, { method: 'GET' })
    }
    log('回放文件可下载', head.status === 200, `status=${head.status} size=${head.headers.get('content-length')}`)
  }

  // ---- 5. 主播主页回放 ----
  const vo = await api('/stream/recordsByAnchor', B.token, { query: { anchorId: B.userId } })
  log('主页回放列表', (vo.data || []).some(r => r.roomId === roomId), `共${(vo.data || []).length}条`)

  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十二 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
