/**
 * 批次十六 E2E：直播间投票
 * 1 非主播发起被拒  2 主播发起 → 观众收 5576（标题+选项）  3 观众投票一人一票
 * 4 延迟 MQ 结算 → 5577 结果（票数正确）  5 结算后无法再投
 * 用法: node scripts/vote_test.mjs
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
  const votes = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) {
        const im = JSON.parse(body)
        if (im.bizCode === 5576) votes.push({ type: 'start', data: JSON.parse(im.data) })
        if (im.bizCode === 5577) votes.push({ type: 'result', data: JSON.parse(im.data) })
      }
    } catch { }
  }
  return { ws, votes, close: () => { clearInterval(hbTimer); ws.close() } }
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

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '投票测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '投票测试间', covertImg: 'x' } })
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

  // ---- 1. 非主播发起被拒 ----
  let vo = await api('/living/vote/create', A.token, {
    query: { roomId, title: '玩什么', options: JSON.stringify(['英雄联盟', '王者']), durationSec: 30 }
  })
  log('非主播发起被拒', vo.data != null, vo.data)

  // ---- 2. 主播发起 ----
  vo = await api('/living/vote/create', B.token, {
    query: { roomId, title: '下一把玩什么', options: JSON.stringify(['英雄联盟', '王者', '原神']), durationSec: 30 }
  })
  log('主播发起投票', vo.data == null, vo.data || '')
  const startA = await waitFor(a.votes, v => v.type === 'start', 8000, 'A收5576')
  log('观众收5576', startA.data.title === '下一把玩什么' && startA.data.options.length === 3,
    `title=${startA.data.title} options=${startA.data.options.length}`)

  // ---- 3. 投票（A 选 0，C 选 0 后又选 1 应被拒） ----
  vo = await api('/living/vote/cast', A.token, { query: { roomId, optionIndex: 0 } })
  log('A投票选项0', vo.data == null, vo.data || '')
  vo = await api('/living/vote/cast', C.token, { query: { roomId, optionIndex: 0 } })
  log('C投票选项0', vo.data == null, vo.data || '')
  vo = await api('/living/vote/cast', C.token, { query: { roomId, optionIndex: 1 } })
  log('C重复投票被拒', vo.data != null, vo.data)
  // 越界选项
  vo = await api('/living/vote/cast', B.token, { query: { roomId, optionIndex: 5 } })
  log('越界选项被拒', vo.data != null, vo.data)

  // ---- 4. 30s 延迟结算 → 5577 ----
  const result = await waitFor(a.votes, v => v.type === 'result', 45000, '5577 结算')
  log('5577结果广播', true, `counts=${result.data.counts} voters=${result.data.votedCount}`)
  log('票数正确(选项0=2票)', result.data.counts[0] === 2, `counts=${JSON.stringify(result.data.counts)}`)

  // ---- 5. 结算后无法再投 ----
  vo = await api('/living/vote/cast', B.token, { query: { roomId, optionIndex: 0 } })
  log('结算后投票被拒', vo.data != null, vo.data)

  // ---- 6. 进房补拉：无进行中投票返回 null ----
  vo = await api('/living/vote/current', B.token, { query: { roomId } })
  log('结算后currentVote=null', vo.data == null)

  a.close(); b.close(); c.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十六 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
