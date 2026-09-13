/**
 * 批次一（风控）E2E：
 * 1 昵称敏感词拦截  2 视频标题拦截  3 评论拦截  4 房间名拦截
 * 5 弹幕替换(*)    6 弹幕拦截(5566)  7 禁言  8 频率风控  9 封号 403
 * 用法: node scripts/risk_control_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const ADMIN = 'http://localhost:38100/live/admin'
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

async function adminApi(path, adminToken, params = {}) {
  const qs = Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${ADMIN}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'adminToken': adminToken }
  })
  return res.json()
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function wsJoin(cfg, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  // 连接后必须先发登录包（code=1001），否则业务消息不处理
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  // 心跳保持 bind key
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
    } catch { /* ignore */ }
  }
  return { ws, received, close: () => { clearInterval(hbTimer); ws.close() } }
}

function sendChat(ws, userId, roomId, content) {
  const body = {
    appId: 10001, userId, bizCode: 5555,
    data: JSON.stringify({ userId, content, roomId, senderName: '测试A', senderAvtar: '' })
  }
  const bodyStr = JSON.stringify(body)
  ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: bodyStr.length, body: bodyStr }))
}

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // 1. 昵称敏感词
  const nick = await api('/user/updateProfile', A.token, { query: { nickName: '我是外挂大师' } })
  log('昵称拦截', nick.code !== 200, `code=${nick.code} msg=${nick.msg}`)

  // 2. 视频标题
  const pub = await api('/video/publish', A.token, { body: { title: '刷单兼职日结', videoUrl: 'http://x/v.mp4' } })
  log('视频标题拦截', pub.code !== 200, `code=${pub.code} msg=${pub.msg}`)

  // 3. 评论
  const cmt = await api('/video/comment/add', A.token, { query: { id: 1, content: '这是诈骗信息' } })
  log('评论拦截', cmt.code !== 200, `code=${cmt.code} msg=${cmt.msg}`)

  // 4. 房间名
  const badRoom = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '赌博天堂', covertImg: 'x' } })
  log('房间名拦截', badRoom.code !== 200, `code=${badRoom.code} msg=${badRoom.msg}`)

  // B 开一个正常房间（开播有频控，失败自动等 65s 重试）
  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '风控测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '风控测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgB = await api('/im/getImConfig', B.token)
  const cfgA = await api('/im/getImConfig', A.token)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  // 登录→进房(房间用户集合)走 MQ 异步，立即发言会与进房竞争导致弹幕被丢，等 3s
  await sleep(3000)

  const bChats = () => b.received.filter(m => m.bizCode === 5555)
  const aNotices = () => a.received.filter(m => m.bizCode === 5566)

  // 5. 弹幕替换
  sendChat(a.ws, A.userId, roomId, '今天垃圾主播来直播了')
  await sleep(2500)
  const replacedMsg = bChats().find(m => String(m.data).includes('*'))
  log('弹幕替换', !!replacedMsg, `B收到=${replacedMsg ? JSON.parse(replacedMsg.data).content : '无'}`)

  // 6. 弹幕拦截
  const bChatCountBefore = bChats().length
  sendChat(a.ws, A.userId, roomId, '快来赌博啊')
  await sleep(2500)
  const notice = aNotices().find(m => String(m.data).includes('敏感'))
  log('弹幕拦截+5566', !!notice, `A收到提示=${notice ? JSON.parse(notice.data).content : '无'}`)
  log('拦截弹幕未广播', bChats().length === bChatCountBefore, `B新增=${bChats().length - bChatCountBefore}`)

  // 7. 禁言
  const adminLogin = await adminApi('/auth/login', '', { username: 'admin', password: 'admin123' })
  if (adminLogin.code !== 200) throw new Error('admin登录失败: ' + JSON.stringify(adminLogin))
  const adminToken = adminLogin.data.token || adminLogin.data
  await adminApi('/user/ban', adminToken, { userId: A.userId, type: 1, minutes: 10, reason: '测试禁言' })
  a.received.length = 0
  sendChat(a.ws, A.userId, roomId, '禁言后还想说话')
  await sleep(2500)
  const muteNotice = aNotices().find(m => String(m.data).includes('禁言'))
  log('禁言生效', !!muteNotice, `A收到=${muteNotice ? JSON.parse(muteNotice.data).content : '无'}`)
  await adminApi('/user/unban', adminToken, { userId: A.userId, type: 1 })
  a.received.length = 0
  sendChat(a.ws, A.userId, roomId, '解禁后又能说话了')
  await sleep(2500)
  log('解禁恢复', aNotices().length === 0, `5566条数=${aNotices().length}`)

  // 8. 频率风控（5s 窗口最多 10 条；先等窗口清零）
  await sleep(5300)
  const before = bChats().length
  for (let i = 0; i < 12; i++) sendChat(a.ws, A.userId, roomId, `刷屏消息第${i}条`)
  await sleep(3000)
  const sent = bChats().length - before
  log('频率风控', sent <= 10, `窗口内12条，B实际收到=${sent}`)

  // 清理房间再测封号（封号后 api 不可用）
  b.close(); a.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })

  // 9. 封号 403
  const balBefore = await api('/bank/account/balance', A.token)
  await adminApi('/user/ban', adminToken, { userId: A.userId, type: 2, minutes: 10, reason: '测试封号' })
  await sleep(500)
  const bannedRes = await fetch(`${GATEWAY}/bank/account/balance`, { method: 'POST', headers: { token: A.token } })
  log('封号403', bannedRes.status === 403, `httpStatus=${bannedRes.status} (解封前正常调用=${balBefore.code})`)
  await adminApi('/user/unban', adminToken, { userId: A.userId, type: 2 })
  await sleep(500)
  const balAfter = await api('/bank/account/balance', A.token)
  log('解封恢复', balAfter.code === 200, `code=${balAfter.code} balance=${balAfter.data}`)
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
