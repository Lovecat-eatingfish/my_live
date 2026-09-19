/**
 * 推流链路端到端验证：登录 → 开播 → 取推流地址 → FFmpeg 推流 →
 * 验证 SRS 收流 / on_publish 回调 / FLV+HLS 播放 → 关播踢流清理
 * 用法: node scripts/e2e/stream_push_test.mjs
 * 依赖: Node 22+、本机 ffmpeg、网关 38080、stream-provider 38090、SRS 1935/1985/8080
 */
import { spawn } from 'node:child_process'

const GATEWAY = 'http://localhost:38080/live/api'
const SRS_API = 'http://127.0.0.1:1985'
const SRS_HTTP = 'http://127.0.0.1:8080'
const PUSH_SECONDS = 12

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}

async function main() {
  // 1. 主播登录
  await api('/userLogin/sendLoginCode', '', { query: { phone: '13800138000' } })
  const loginVO = await api('/userLogin/login', '', { query: { phone: '13800138000', code: '123456' } })
  if (loginVO.code !== 200) throw new Error('登录失败: ' + loginVO.msg)
  const anchor = loginVO.data
  log('主播登录', true, `userId=${anchor.userId}`)

  // 2. 开播
  const startVO = await api('/living/startingLiving', anchor.token, { query: { type: 1 } })
  if (startVO.code !== 200) throw new Error('开播失败: ' + startVO.msg)
  const roomId = startVO.data.roomId
  log('开播', true, `roomId=${roomId}`)

  // 3. 取推流地址（后端签发 streamKey）
  const pushVO = await api('/stream/createPushUrl', anchor.token, { query: { roomId } })
  log('createPushUrl', pushVO.code === 200, `code=${pushVO.code} msg=${pushVO.msg}`)
  if (pushVO.code !== 200) throw new Error('获取推流地址失败')
  const { pushUrl, streamKey } = pushVO.data
  console.log(`   streamKey = ${streamKey}`)
  console.log(`   pushUrl   = ${pushUrl}`)

  // 4. FFmpeg 推流（本机生成测试画面 + 音频）
  console.log(`\n—— FFmpeg 推流 ${PUSH_SECONDS}s（testsrc2 + 1kHz 音频）——`)
  const ffmpeg = spawn('ffmpeg', [
    '-loglevel', 'warning',
    '-re', '-f', 'lavfi', '-i', 'testsrc2=size=640x480:rate=25',
    '-f', 'lavfi', '-i', 'sine=frequency=1000',
    '-c:v', 'libx264', '-preset', 'ultrafast', '-tune', 'zerolatency', '-g', '50',
    '-c:a', 'aac', '-b:a', '64k', '-t', String(PUSH_SECONDS),
    '-f', 'flv', pushUrl
  ], { stdio: ['ignore', 'pipe', 'pipe'] })
  let ffErr = ''
  ffmpeg.stderr.on('data', d => { ffErr += d.toString() })
  const ffDone = new Promise(r => ffmpeg.on('close', r))

  // 5. 轮询 SRS 确认收到流 + 后端状态联动
  let srsStream = null, statusVO = null
  for (let i = 0; i < 10; i++) {
    await sleep(1500)
    const st = await fetch(`${SRS_API}/api/v1/streams/`).then(r => r.json())
    srsStream = (st.streams || []).find(s => s.name === streamKey)
    statusVO = await api('/stream/status', anchor.token, { query: { roomId } })
    if (srsStream?.publish?.active) break
  }
  log('SRS收到推流', !!srsStream?.publish?.active,
    srsStream ? `stream=${srsStream.name} publish.active=${srsStream.publish?.active} clients=${srsStream.clients}` : '未发现流')
  log('on_publish回调→后端状态', statusVO?.code === 200 && statusVO?.data?.status === 1,
    `code=${statusVO?.code} status=${statusVO?.data?.status} viewerCount=${statusVO?.data?.viewerCount}`)

  // 6. 播放验证：HTTP-FLV 与 HLS
  await sleep(2000)
  let flvBytes = 0
  try {
    const ac = new AbortController()
    const t = setTimeout(() => ac.abort(new Error('sample-done')), 2000)
    const res = await fetch(`${SRS_HTTP}/live/${streamKey}.flv`, { signal: ac.signal })
    const reader = res.body.getReader()
    while (flvBytes < 65536) {
      const { done, value } = await reader.read()
      if (done) break
      flvBytes += value.length
    }
    clearTimeout(t)
    ac.abort()
  } catch { /* 采样结束即视为成功 */ }
  log('HTTP-FLV播放', flvBytes > 1024, `采样 ${flvBytes} 字节 FLV 媒体数据`)
  let hls = await fetch(`${SRS_HTTP}/live/${streamKey}.m3u8`).then(r => r.text()).catch(() => '')
  const inner = hls.split('\n').find(l => l.endsWith('.m3u8') || l.includes('.m3u8?'))
  if (inner) hls = await fetch(new URL(inner, `${SRS_HTTP}/live/`)).then(r => r.text()).catch(() => '')
  log('HLS播放', hls.includes('#EXTINF'), hls.includes('#EXTINF') ? `${hls.split('\n').filter(l => l.endsWith('.ts')).length} 个 ts 分片` : 'm3u8 获取失败')

  // 7. 等推流自然结束，验证关播联动（closeLiving 踢流 → on_unpublish）
  await ffDone
  log('FFmpeg推流结束', ffmpeg.exitCode === 0, ffmpeg.exitCode !== 0 ? ffErr.slice(-300) : '')
  const closeVO = await api('/living/closeLiving', anchor.token, { query: { roomId } })
  log('关播closeLiving', closeVO.code === 200, `code=${closeVO.code} msg=${closeVO.msg}`)
  await sleep(1500)
  const after = await fetch(`${SRS_API}/api/v1/streams/`).then(r => r.json())
  const still = (after.data || []).find(s => s.name === streamKey)
  log('关播后SRS流清理', !still || !still.publish?.active, still ? `publish.active=${still.publish?.active}` : '流已销毁')

  console.log('\n—— 推流链路验证完成 ——')
  process.exit(process.exitCode || 0)
}

main().catch(e => { console.error('❌ [异常]', e.message); process.exit(1) })
