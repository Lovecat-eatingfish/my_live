/**
 * 批次十五 E2E：互关标记 + 粉丝灯牌 + 进场特效
 * 1 互关：A↔B 互关后 profile.isMutual=true  2 送礼积累粉丝亲密度（B 主播的粉丝团）
 * 3 弹幕带 fanLevel（送礼者对房间主播）  4 每日观看 +10（进房欢迎消息 fanLevel 字段存在）
 * 5 进场特效门槛：欢迎消息 content 含灯牌文案（fanLevel≥3）
 * 用法: node scripts/e2e/fan_badge_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const MAGIC = 19231

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST', headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
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
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: MAGIC, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  await sleep(800)
  const chatMsgs = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) {
        const im = JSON.parse(body)
        if (im.bizCode === 5555) chatMsgs.push(JSON.parse(im.data))
      }
    } catch { }
  }
  return { ws, chatMsgs, sendChat(content) {
    const data = JSON.stringify({ userId, content, roomId, senderName: '测试', senderAvtar: '' })
    const body = JSON.stringify({ appId: 10001, userId, bizCode: 5555, data })
    ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: body.length, body }))
  }, close: () => { clearInterval(hbTimer); ws.close() } }
}

const run = async () => {
  const A = await login('13876543210')   // 观众
  const B = await login('13876543211')   // 主播
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 1. 互关 ----
  await api('/user/follow', A.token, { query: { followUserId: B.userId } })
  await api('/user/follow', B.token, { query: { followUserId: A.userId } })
  let vo = await api('/user/profile', A.token, { query: { targetUserId: B.userId } })
  log('A看B:互关标记', vo.data?.isMutual === true, `isFollow=${vo.data?.isFollow} isMutual=${vo.data?.isMutual}`)
  await api('/user/unfollow', B.token, { query: { followUserId: A.userId } })
  vo = await api('/user/profile', A.token, { query: { targetUserId: B.userId } })
  log('B取关后互关消失', vo.data?.isMutual === false, `isMutual=${vo.data?.isMutual}`)
  await api('/user/follow', B.token, { query: { followUserId: A.userId } }) // 恢复

  // ---- 2. B 开播 ----
  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '灯牌测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '灯牌测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const cfgB = await api('/im/getImConfig', B.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  await sleep(2500)

  // ---- 3. 每日观看 +10（欢迎消息带 fanLevel） ----
  let welcome = null
  for (let i = 0; i < 24; i++) { // 欢迎走 Dubbo 冷连接，轮询等 12s
    welcome = b.chatMsgs.find(m => m.system && m.fanLevel !== undefined)
    if (welcome) break
    await sleep(500)
  }
  log('欢迎消息带fanLevel字段', !!welcome, welcome ? `fanLevel=${welcome.fanLevel} content=${welcome.content}` : '未收到')

  // ---- 4. 送礼积累亲密度 → 弹幕 fanLevel 上升 ----
  const gifts = await api('/gift/listGift', A.token)
  const big = (gifts.data || []).reduce((m, g) => (Number(g.price) > Number(m.price) ? g : m), (gifts.data || [])[0])
  // 用大礼物一次性刷到 L1(≥1) 与多送几次到 L2(≥100)
  for (let i = 0; i < 3; i++) {
    await api('/gift/send', A.token, { body: { giftId: big.giftId ?? big.id, roomId, receiverId: B.userId, type: 0 } })
    await sleep(3500) // MQ 扣费+分账+亲密度
  }
  a.sendChat('灯牌测试弹幕')
  await sleep(2500)
  const danmu = b.chatMsgs.filter(m => !m.system && m.content === '灯牌测试弹幕').pop()
  log('弹幕带fanLevel', danmu && danmu.fanLevel !== undefined && danmu.fanLevel >= 1,
    `fanLevel=${danmu?.fanLevel} 礼物=${big.price}x3`)

  a.close(); b.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十五 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
