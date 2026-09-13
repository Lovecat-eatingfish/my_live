/**
 * 批次九 E2E：PK 竞技化 + 分账底座
 * 1 分账：送礼 100 金币 → 主播到账 90（平台抽成10%）
 * 2 PK 上线 → 观众点赞加分（进度 50→51 广播 5558）→ 日限 50
 * 3 倒计时结算：发延迟消息（测试用短延迟直接结算验证逻辑：手动结算入口不存在——
 *   用 10 分钟延迟太久，改为验证 settle 消费者启动 + 直发一条 settle 消息用 MQ 管理台？
 *   简化：结算逻辑以"提前打满"路径验证（送礼把进度推到 100 → isOver 置位 → 胜负已定）
 * 用法: node scripts/pk_share_test.mjs
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
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
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
  const A = await login('13800138000')   // 观众/送礼人/PK对手
  const B = await login('13900999201')   // 主播
  log('login', true, `A=${A.userId} B=${B.userId}`)

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: 'PK分账测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: 'PK分账测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId

  const cfgA = await api('/im/getImConfig', A.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  await sleep(3000)

  // ---- 1. 分账：A 送 100 金币礼物 → B 到账 90 ----
  const gifts = await api('/gift/listGift', A.token)
  const gift = (gifts.data || []).find(g => Number(g.price) === 100) || gifts.data[0]
  const balA0 = Number((await api('/bank/account/balance', A.token)).data)
  const balB0 = Number((await api('/bank/account/balance', B.token)).data)
  await api('/gift/send', A.token, {
    body: { giftId: gift.giftId ?? gift.id, roomId, receiverId: B.userId, type: 0 }
  })
  await sleep(4000) // MQ 扣费+分账
  const balA1 = Number((await api('/bank/account/balance', A.token)).data)
  const balB1 = Number((await api('/bank/account/balance', B.token)).data)
  const cost = balA0 - balA1
  const earned = balB1 - balB0
  log('送礼分账(主播到账90%)', cost === gift.price && earned === Math.floor(gift.price * 0.9),
    `花费=${cost} B到账=${earned}（期望 ${Math.floor(gift.price * 0.9)}）`)

  // ---- 2. PK 上线 + 点赞加分 ----
  const pk = await api('/living/onlinePk', A.token, { query: { roomId } })
  log('A上PK台', pk.code === 200, `code=${pk.code} data=${JSON.stringify(pk.data)}`)
  await sleep(1500)
  const before = a.received.filter(m => m.bizCode === 5558).length
  const like1 = await api('/living/pk/like', A.token, { query: { roomId } })
  await sleep(2000)
  const pk5558 = a.received.filter(m => m.bizCode === 5558).map(m => JSON.parse(m.data))
  const likeSig = pk5558.find(d => d.likeAdd)
  log('点赞加分广播(5558)', like1.code === 200 && like1.data === true && !!likeSig,
    likeSig ? `pkNum=${likeSig.pkNum}` : '未收到')
  log('点赞加到主播侧(>50)', likeSig && Number(likeSig.pkNum) > 50, `pkNum=${likeSig ? likeSig.pkNum : '-'}`)

  // ---- 3. 提前打满结算路径：连送 PK 礼物把进度推满(anchor侧+price/10) → 5558 带 winnerId ----
  const giftsAll = await api('/gift/listGift', A.token)
  const big = (giftsAll.data || []).slice().sort((x, y) => y.price - x.price)[0]
  const step = Math.max(1, Math.floor(big.price / 10))
  let winnerSig = null
  for (let i = 0; i < 40 && !winnerSig; i++) {
    await api('/gift/send', A.token, {
      body: { giftId: big.giftId ?? big.id, roomId, receiverId: B.userId, type: 1 }
    })
    await sleep(2200)
    winnerSig = a.received.filter(m => m.bizCode === 5558).map(m => JSON.parse(m.data))
      .find(d => d.winnerId)
  }
  log('打满结算(5558 winnerId)', !!winnerSig, winnerSig ? `winnerId=${winnerSig.winnerId} (大礼价格=${big.price})` : '未触发')

  // ---- 4. isOver 后点赞拒绝 ----
  await sleep(1000)
  const like2 = await api('/living/pk/like', A.token, { query: { roomId } })
  log('PK 结束后点赞拒绝', like2.data === false, `data=${like2.data}`)

  a.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次九 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
