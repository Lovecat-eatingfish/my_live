/**
 * 批次十三 E2E：直播间口令抽奖
 * 1 非主播发起被拒  2 时长不合法被拒  3 发起扣奖池（B 余额 -10）+ 观众收 5574
 * 4 弹幕命中口令参与（SADD 去重，2 人）  5 延迟 MQ 到点结算（30s 档）→ 5575 中奖名单 + 中奖者到账 10
 * 6 无人参与的抽奖全额退回主播（+6）
 * 用法: node scripts/e2e/lottery_test.mjs
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
  return {
    ws, received,
    sendChat(content) {
      const data = JSON.stringify({ userId, content, roomId, senderName: '测试', senderAvtar: '' })
      const body = JSON.stringify({ appId: 10001, userId, bizCode: 5555, data })
      ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: body.length, body }))
    },
    close: () => { clearInterval(hbTimer); ws.close() }
  }
}

async function waitFor(list, pred, timeoutMs = 45000, label = '') {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const hit = list.find(pred)
    if (hit) return hit
    await sleep(500)
  }
  throw new Error(`等待超时: ${label}`)
}

const run = async () => {
  const A = await login('13876543210')   // 观众1
  const B = await login('13876543211')   // 主播
  const C = await login('13876543212')   // 观众2
  log('login', true, `A=${A.userId} B=${B.userId} C=${C.userId}`)

  // 充值兜底：主播/观众余额不足时走 mock 支付充 1000
  async function topup(token) {
    const bal = Number((await api('/bank/account/balance', token)).data)
    if (bal < 50) {
      const prod = await api('/bank/products', token, { query: { type: 0 } })
      const target = (prod.data?.payProductItemVOList || []).find(p => p.coinNum === 1000) || (prod.data?.payProductItemVOList || [])[0]
      await api('/bank/payProduct', token, { query: { productId: target.id, paySource: 2, payChannel: 1 } })
      await sleep(3000)
    }
  }
  await topup(B.token); await topup(A.token); await topup(C.token)
  log('余额兜底充值', true)

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '抽奖测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '抽奖测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const cfgB = await api('/im/getImConfig', B.token)
  const cfgC = await api('/im/getImConfig', C.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  const c = await wsJoin(cfgC.data, C.userId, roomId)
  await sleep(2500)

  const balA0 = Number((await api('/bank/account/balance', A.token)).data)
  const balC0 = Number((await api('/bank/account/balance', C.token)).data)

  // ---- 1/2. 鉴权与参数校验 ----
  let vo = await api('/living/lottery/create', A.token, { query: { roomId, keyword: '666', durationSec: 30, winnerCount: 1, rewardCoins: 0 } })
  log('非主播发起被拒', vo.data != null, `msg=${vo.data}`)
  vo = await api('/living/lottery/create', B.token, { query: { roomId, keyword: '666', durationSec: 45, winnerCount: 1, rewardCoins: 0 } })
  log('时长不合法被拒', vo.data != null, `msg=${vo.data}`)

  // ---- 3. 发起（奖池 10） ----
  const balB0 = Number((await api('/bank/account/balance', B.token)).data)
  vo = await api('/living/lottery/create', B.token, { query: { roomId, keyword: '旗鱼666', durationSec: 30, winnerCount: 1, rewardCoins: 10 } })
  log('主播发起成功', vo.data == null, vo.data || '')
  await sleep(1500)
  const balB1 = Number((await api('/bank/account/balance', B.token)).data)
  log('发起即扣奖池10', balB0 - balB1 === 10, `扣除=${balB0 - balB1}`)
  const startA = await waitFor(a.received, m => m.bizCode === 5574, 8000, 'A收5574')
  const startData = JSON.parse(startA.data)
  log('观众收5574', true, `keyword=${startData.keyword} 奖池=${startData.rewardCoins}`)

  // ---- 4. 弹幕命中口令参与 ----
  a.sendChat('旗鱼666')
  await sleep(1200)
  a.sendChat('聊聊别的')          // 不命中
  c.sendChat('旗鱼666')
  c.sendChat('旗鱼666')           // 重复参与应去重
  await sleep(1000)
  b.sendChat('旗鱼666看看')       // 非精确命中，不参与

  // ---- 5. 30s 延迟结算 ----
  const resultA = JSON.parse((await waitFor(a.received, m => m.bizCode === 5575, 45000, '5575 结算')).data)
  const winners = resultA.winners || []
  log('5575开奖广播', true, `参与=${resultA.participantCount} 中奖=${winners} 每人=${resultA.rewardPerPerson}`)
  log('参与者去重(2人)', resultA.participantCount === 2, `participantCount=${resultA.participantCount}`)
  const winnerId = Number(winners[0])
  const winnerToken = winnerId === A.userId ? A.token : C.token
  const winnerBefore = winnerId === A.userId ? balA0 : balC0
  await sleep(1500) // incr 到账
  const balW = Number((await api('/bank/account/balance', winnerToken)).data)
  log('中奖者到账10', balW - winnerBefore === 10, `到账=${balW - winnerBefore}`)

  // ---- 6. 无人参与全额退回 ----
  const balB2 = Number((await api('/bank/account/balance', B.token)).data)
  vo = await api('/living/lottery/create', B.token, { query: { roomId, keyword: '没人玩', durationSec: 30, winnerCount: 1, rewardCoins: 6 } })
  log('第二次发起', vo.data == null, vo.data || '')
  await sleep(42000)
  const balB3 = Number((await api('/bank/account/balance', B.token)).data)
  // 发起时扣 6，无人参与结算退回 6，净值应为 0
  log('无人参与全额退回', balB3 === balB2, `扣6退6净值=${balB3 - balB2}`)
  const resB = JSON.parse((await waitFor(b.received, m => {
    if (m.bizCode !== 5575) return false
    return JSON.parse(m.data).keyword === '没人玩'
  }, 8000, 'B收5575(无人)')).data)
  log('5575无人参与广播', resB.participantCount === 0, `participants=${resB.participantCount}`)

  a.close(); b.close(); c.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十三 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
