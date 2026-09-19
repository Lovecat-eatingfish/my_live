/**
 * 批次十四 E2E：直播间治理（公告 + 房间管理员 + 房间禁言）
 * 1 非主播设公告被拒  2 主播设公告→anchorConfig 可见  3 任命管理员（仅主播）
 * 4 管理员禁言观众→观众弹幕被拦(5566)  5 主播不能被禁言  6 解禁恢复  7 移除管理员后失去禁言权
 * 用法: node scripts/e2e/room_governance_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const MAGIC = 19231

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
  const notices = [] // 5566 拦截提示
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) {
        const im = JSON.parse(body)
        if (im.bizCode === 5566) notices.push(JSON.parse(im.data).content)
      }
    } catch { }
  }
  return {
    ws, notices,
    sendChat(content) {
      const data = JSON.stringify({ userId, content, roomId, senderName: '测试', senderAvtar: '' })
      const body = JSON.stringify({ appId: 10001, userId, bizCode: 5555, data })
      ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: body.length, body }))
    },
    close: () => { clearInterval(hbTimer); ws.close() }
  }
}

const run = async () => {
  const A = await login('13876543210')   // 观众（后被任命为管理员）
  const B = await login('13876543211')   // 主播
  const C = await login('13876543212')   // 普通观众
  log('login', true, `A=${A.userId} B=${B.userId} C=${C.userId}`)

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '治理测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '治理测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const cfgB = await api('/im/getImConfig', B.token)
  const cfgC = await api('/im/getImConfig', C.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  const c = await wsJoin(cfgC.data, C.userId, roomId)
  await sleep(2500)

  // ---- 1. 非主播设公告被拒 ----
  let vo = await api('/living/setAnnouncement', C.token, { query: { roomId, announcement: '我要篡改公告' } })
  log('非主播设公告被拒', vo.data != null, vo.data)

  // ---- 2. 主播设公告 → anchorConfig 可见 ----
  vo = await api('/living/setAnnouncement', B.token, { query: { roomId, announcement: '欢迎来到治理测试间，文明弹幕~' } })
  log('主播设公告', vo.data == null, vo.data || '')
  vo = await api('/living/anchorConfig', C.token, { query: { roomId } })
  log('观众看到公告', vo.data?.announcement?.includes('文明弹幕'), vo.data?.announcement)

  // ---- 3. 任命管理员 ----
  vo = await api('/living/roomAdmin/appoint', C.token, { query: { roomId, adminUserId: A.userId } })
  log('非主播任命被拒', vo.data != null, vo.data)
  vo = await api('/living/roomAdmin/appoint', B.token, { query: { roomId, adminUserId: A.userId } })
  log('任命A为管理员', vo.data == null, vo.data || '')
  vo = await api('/living/anchorConfig', A.token, { query: { roomId } })
  log('A的isRoomAdmin=true', vo.data?.isRoomAdmin === true)
  log('主播不能任命自己', (await api('/living/roomAdmin/appoint', B.token, { query: { roomId, adminUserId: B.userId } })).data != null)

  // ---- 4. 管理员禁言观众 → C 弹幕被拦 ----
  vo = await api('/living/roomAdmin/mute', A.token, { query: { roomId, muteUserId: C.userId, minutes: 30 } })
  log('管理员禁言C', vo.data == null, vo.data || '')
  c.sendChat('我被禁言了吗')
  await sleep(2000)
  log('C收到禁言提示', c.notices.some(n => n.includes('禁言')), (c.notices || []).join('|'))

  // ---- 5. 主播不能被禁言 ----
  vo = await api('/living/roomAdmin/mute', A.token, { query: { roomId, muteUserId: B.userId, minutes: 30 } })
  log('管理员禁言主播被拒', vo.data != null, vo.data)

  // ---- 6. 解禁恢复 ----
  vo = await api('/living/roomAdmin/unmute', A.token, { query: { roomId, muteUserId: C.userId } })
  log('解禁C', vo.data === true)
  await sleep(500)
  const before = c.notices.length
  c.sendChat('解禁后可以说话了')
  await sleep(2000)
  log('解禁后不再拦截', c.notices.length === before)

  // ---- 7. 移除管理员后失去禁言权 ----
  vo = await api('/living/roomAdmin/remove', B.token, { query: { roomId, adminUserId: A.userId } })
  log('移除A管理员', vo.data === true)
  vo = await api('/living/roomAdmin/mute', A.token, { query: { roomId, muteUserId: C.userId, minutes: 5 } })
  log('失去禁言权', vo.data != null, vo.data)

  a.close(); c.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十四 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
