/**
 * 批次五 E2E：仪表盘 + 视频审核流 + 截帧审阅（真实推流）+ 敏感词页后端
 * 1 仪表盘四指标  2 审核流(发布→队列→通过上线/驳回)  3 截帧(ffmpeg 推真实流→30s 截帧→处置警告/强关)
 * 用法: node scripts/e2e/admin_audit_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const ADMIN = 'http://localhost:38100/live/admin'
const { execSync, spawn } = await import('node:child_process')
const fs = await import('node:fs')

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}

async function adminApi(path, token, params = {}) {
  const qs = Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${ADMIN}${path}${qs ? '?' + qs : ''}`, { method: 'POST', headers: { adminToken: token } })
    .then(r => r.json())
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  const AL = await fetch(`${ADMIN}/auth/login?username=admin&password=admin123`, { method: 'POST' }).then(r => r.json())
  const adminToken = AL.data?.token || AL.data
  if (!adminToken) throw new Error('admin 登录失败')
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 1. 仪表盘 ----
  const dash = await adminApi('/stats/dashboard', adminToken)
  const d = dash.data || {}
  log('仪表盘四指标', dash.code === 200 &&
    ['newUsers', 'openRooms', 'rechargeYuan', 'tradeCount'].every(k => d[k] !== undefined),
    `newUsers=${d.newUsers} openRooms=${d.openRooms} rechargeYuan=${d.rechargeYuan} tradeCount=${d.tradeCount}`)

  // ---- 2. 审核流（真视频，发布默认 status=2 审核中） ----
  execSync('ffmpeg -y -f lavfi -i testsrc=duration=5:size=320x240:rate=15 ' +
    '-f lavfi -i sine=duration=5 -c:v libx264 -preset veryfast -c:a aac -pix_fmt yuv420p video-e2e-audit.mp4', { cwd: process.cwd() })
  const buf = fs.readFileSync('video-e2e-audit.mp4')
  const up = async (name) => {
    const form = new FormData()
    form.append('file', new Blob([buf]), name)
    return fetch(`${GATEWAY}/video/uploadVideo`, { method: 'POST', headers: { token: A.token }, body: form }).then(r => r.json())
  }
  const up1 = await up('a.mp4')
  const pub1 = await api('/video/publish', A.token, { body: { title: '批次五审核通过测试', videoUrl: up1.data } })
  const v1 = pub1.data
  const q1 = await adminApi('/video/reviewList', adminToken, { page: 1, pageSize: 50 })
  const inQueue = (q1.data?.list || []).some(v => Number(v.id) === Number(v1))
  log('发布进入审核队列(状态2)', pub1.code === 200 && inQueue, `videoId=${v1}`)
  await adminApi('/video/review', adminToken, { id: v1, pass: true })
  // detail 需等转码完成（处理中不可见）
  let d1 = null
  for (let i = 0; i < 40; i++) {
    await sleep(3000)
    d1 = await api('/video/detail', A.token, { query: { id: v1 } })
    if (d1.code === 200 && d1.data?.item) break
  }
  log('审核通过后上线(详情可见)', d1?.code === 200 && d1.data?.item?.status === 1, `status=${d1?.data?.item?.status}`)

  const up2 = await up('b.mp4')
  const pub2 = await api('/video/publish', A.token, { body: { title: '批次五驳回测试', videoUrl: up2.data } })
  const v2 = pub2.data
  await adminApi('/video/review', adminToken, { id: v2, pass: false })
  await sleep(2500)
  const d2self = await api('/video/detail', A.token, { query: { id: v2 } })
  const feed = await api('/video/feed', A.token, { query: { size: 20 } })
  const inFeed = (feed.data || []).some(v => Number(v.id) === Number(v2))
  log('驳回后作者可见/Feed不可见', d2self.data?.item != null && !inFeed, `status=${d2self.data?.item?.status} inFeed=${inFeed}`)
  fs.unlinkSync('video-e2e-audit.mp4')

  // ---- 3. 截帧审阅（真实推流） ----
  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '截帧巡查测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '截帧巡查测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  const push = await api('/stream/createPushUrl', B.token, { query: { roomId } })
  const pushUrl = typeof push.data === 'string' ? push.data : push.data?.pushUrl
  log('B开播+拿推流地址', !!pushUrl, `roomId=${roomId} url=${pushUrl}`)
  const streamKey = pushUrl.split('/').pop()

  // ffmpeg 真实推流 3 分钟（后台），SRS on_publish 回调 → streamStatus=1 → 30s 内截帧
  const ff = spawn('ffmpeg', ['-re', '-f', 'lavfi', '-i', 'testsrc=duration=240:size=640x360:rate=20',
    '-f', 'lavfi', '-i', 'sine=duration=240', '-c:v', 'libx264', '-preset', 'veryfast',
    '-c:a', 'aac', '-f', 'flv', pushUrl], { stdio: 'ignore' })

  let snapshot = null
  for (let i = 0; i < 20; i++) {
    await sleep(6000)
    const sl = await adminApi('/snapshot/list', adminToken, { status: 0, page: 1, pageSize: 50 })
    snapshot = (sl.data?.list || []).find(s => Number(s.room_id) === Number(roomId))
    if (snapshot) break
  }
  log('巡查截帧落库', !!snapshot, snapshot ? `img=${snapshot.img_url.slice(0, 60)}...` : '未出现（90s 内）')

  if (snapshot) {
    const warn = await adminApi('/snapshot/handle', adminToken, { id: snapshot.id, action: 'warn' })
    log('处置:警告(5566发给主播)', warn.code === 200)
  }

  // 强制下播（处置动作复用关播链路）：轮询等下一轮 30s 截帧生成新待审记录（最多 75s，消除单次 sleep 的时序竞态）
  let snap2hit = null
  for (let i = 0; i < 15 && !snap2hit; i++) {
    await sleep(5000)
    const sl = await adminApi('/snapshot/list', adminToken, { status: 0, page: 1, pageSize: 50 })
    snap2hit = (sl.data?.list || []).find(s => Number(s.room_id) === Number(roomId))
  }
  if (snap2hit) {
    const close = await adminApi('/snapshot/handle', adminToken, { id: snap2hit.id, action: 'close' })
    await sleep(1000)
    const my = await api('/living/myLivingRoom', B.token)
    log('处置:强制下播', close.code === 200 && (my.data === null || Number(my.data) !== Number(roomId)),
      `myLivingRoom=${my.data}`)
  } else {
    await api('/living/closeLiving', B.token, { query: { roomId } })
    log('处置:强制下播', false, '无第二张截帧可用，跳过（75s 内未生成）')
  }
  try { ff.kill() } catch { }

  // ---- 4. 敏感词接口（页面后端复用批次一链路） ----
  const rwAdd = await adminApi('/riskWord/add', adminToken, { word: '批次五测试词', level: 1, scene: 0 })
  const rwList = await adminApi('/riskWord/list', adminToken)
  const added = (rwList.data || []).find(w => w.word === '批次五测试词')
  if (added) await adminApi('/riskWord/delete', adminToken, { id: added.id })
  log('敏感词增删查', rwAdd.code === 200 && !!added, '')

  fs.writeFileSync('admin_audit_done.tmp', '1')
  fs.unlinkSync('admin_audit_done.tmp')
  console.log('\n批次五 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
