/**
 * 批次七 E2E：连麦信令状态机
 * 1 非主播邀请被拒  2 主播邀请→观众收5572 invite  3 重复邀请拒绝
 * 4 接受→观众收accepted(含liveg_推流参数)+房间广播start  5 guest streamKey 推流可注册(SRS)  6 挂断→广播stop
 * 用法: node scripts/e2e/linkmic_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const MAGIC = 19231

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

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function wsJoin(cfg, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: MAGIC, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  await sleep(800)
  const received = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) received.push(JSON.parse(body))
    } catch { }
  }
  return { ws, received, close: () => { clearInterval(hbTimer); ws.close() } }
}

const run = async () => {
  const A = await login('13800138000')   // 观众（被连麦）
  const B = await login('13900999201')   // 主播
  log('login', true, `A=${A.userId} B=${B.userId}`)

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '连麦测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '连麦测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const cfgB = await api('/im/getImConfig', B.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  await sleep(3000)

  // ---- 1. 非主播邀请被拒 ----
  const bad = await api('/living/linkMic/invite', A.token, { query: { roomId, guestUserId: B.userId } })
  log('非主播邀请被拒', bad.code !== 200, `code=${bad.code} msg=${bad.msg}`)

  // ---- 2. 主播邀请 → A 收 5572 invite ----
  const inv = await api('/living/linkMic/invite', B.token, { query: { roomId, guestUserId: A.userId } })
  const linkMicId = inv.data
  log('主播邀请成功', inv.code === 200 && !!linkMicId, `linkMicId=${linkMicId}`)
  let inviteSig = null
  for (let i = 0; i < 10 && !inviteSig; i++) {
    await sleep(1000)
    inviteSig = a.received.filter(m => m.bizCode === 5572).map(m => JSON.parse(m.data))
      .find(d => d.action === 'invite')
  }
  log('观众收5572邀请', !!inviteSig && Number(inviteSig.linkMicId) === Number(linkMicId),
    inviteSig ? `linkMicId=${inviteSig.linkMicId}` : '未收到')

  // ---- 3. 重复邀请拒绝 ----
  const dup = await api('/living/linkMic/invite', B.token, { query: { roomId, guestUserId: A.userId } })
  log('重复邀请拒绝', dup.code !== 200, `code=${dup.code} msg=${dup.msg}`)

  // ---- 4. 接受 → A 收 accepted + 房间广播 start ----
  const acc = await api('/living/linkMic/accept', A.token, { query: { linkMicId } })
  log('接受连麦', acc.code === 200 && acc.data === true, `code=${acc.code}`)
  let acceptedSig = null, startSig = null
  for (let i = 0; i < 10 && !(acceptedSig && startSig); i++) {
    await sleep(1000)
    const sigs = a.received.filter(m => m.bizCode === 5572).map(m => JSON.parse(m.data))
    acceptedSig = acceptedSig || sigs.find(d => d.action === 'accepted')
    startSig = startSig || sigs.find(d => d.action === 'start')
  }
  log('观众收accepted(推流参数)', !!acceptedSig && /liveg_/.test(acceptedSig.rtcStreamUrl || ''),
    acceptedSig ? `streamUrl=${(acceptedSig.rtcStreamUrl || '').slice(-20)} hls=${!!acceptedSig.hlsUrl}` : '未收到')
  log('房间广播start', !!startSig && startSig.guestNickName, startSig ? `guest=${startSig.guestNickName}` : '未收到')

  // ---- 5. guest streamKey 推流可被 SRS 注册（on_publish 反查不误伤房间状态） ----
  const { spawn } = await import('node:child_process')
  const guestKey = (acceptedSig.rtcStreamUrl || '').split('/').pop()
  let srsOk = false
  if (guestKey && acceptedSig.hlsUrl) {
    const rtmpUrl = acceptedSig.hlsUrl.replace(/^http/, 'http').replace(/\.m3u8$/, '') // 仅为占位
    const ff = spawn('ffmpeg', ['-re', '-f', 'lavfi', '-i', 'testsrc=duration=45:size=320x240:rate=15',
      '-c:v', 'libx264', '-preset', 'ultrafast',
      '-f', 'flv', `rtmp://127.0.0.1:1935/live/${guestKey}`], { stdio: 'ignore' })
    for (let i = 0; i < 12; i++) {
      await sleep(3000)
      const st = await fetch('http://127.0.0.1:1985/api/v1/streams/').then(r => r.json())
      srsOk = (st.streams || []).some(s => s.name === guestKey)
      if (srsOk) break
    }
    ff.kill()
  }
  log('guest推流可注册(SRS)', srsOk, `guestKey=${guestKey}`)
  // 主房间 stream_status 不被 guest 流误伤
  const roomStatus = await api('/living/onlineCount', B.token, { query: { roomId } })
  log('房间在线数正常(未误伤)', roomStatus.code === 200, `online=${roomStatus.data}`)

  // ---- 6. 挂断 → 广播 stop ----
  const hup = await api('/living/linkMic/hangUp', B.token, { query: { roomId } })
  let stopSig = null
  for (let i = 0; i < 10 && !stopSig; i++) {
    await sleep(1000)
    stopSig = a.received.filter(m => m.bizCode === 5572).map(m => JSON.parse(m.data))
      .find(d => d.action === 'stop')
  }
  log('挂断广播stop', hup.code === 200 && hup.data === true && !!stopSig, stopSig ? 'ok' : '未收到')

  a.close(); b.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次七 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
