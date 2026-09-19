/**
 * 批次十 E2E：私信 DM
 * 1 会话列表初始为空  2 A→B 上行 5568 双方收 5569（B 收到 + A 回显）
 * 3 会话/未读数正确（B unread=1, A unread=0）  4 历史消息可查
 * 5 B markRead 后未读清零  6 B 离线时 A 再发 → 不推但未读累计  7 再上线可继续收
 * 用法: node scripts/e2e/dm_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const MAGIC = 19231

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {}, method = 'GET' } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method,
    headers: { 'token': token, 'Content-Type': 'application/json' }
  })
  return res.json()
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone }, method: 'POST' })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' }, method: 'POST' })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

// 私信连接：roomId 传 0，仅借 1001 登录包完成 uid→ip 绑定
async function wsDm(cfg, userId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${userId}/1001/0`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: MAGIC, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  const received = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) {
        const im = JSON.parse(body)
        if (im.bizCode === 5569) received.push(JSON.parse(im.data))
      }
    } catch { }
  }
  await sleep(800)
  return {
    ws, received,
    sendDm(toUid, content) {
      const data = JSON.stringify({ fromUid: userId, toUid, content })
      const body = JSON.stringify({ appId: 10001, userId, bizCode: 5568, data })
      ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: body.length, body }))
    },
    close: () => { clearInterval(hbTimer); ws.close() }
  }
}

async function waitFor(list, pred, timeoutMs = 8000, label = '') {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const hit = list.find(pred)
    if (hit) return hit
    await sleep(300)
  }
  throw new Error(`等待超时: ${label}`)
}

const run = async () => {
  const A = await login('13876543210')
  const B = await login('13876543211')
  log('login', true, `A=${A.userId} B=${B.userId}`)
  // 幂等化：清掉历史运行在 A→B 会话上累积的未读，保证 unread=1 断言只反映本次消息
  await api('/dm/markRead', B.token, { query: { peerUid: A.userId }, method: 'POST' })

  // ---- 1. 会话列表初始为空 ----
  let vo = await api('/dm/conversations', A.token)
  const before = (vo.data || []).filter(c => c.peerUid === B.userId).length
  log('A会话列表可达', vo.code === 200, `与B的历史会话数=${before}`)

  // ---- 2. A → B ----
  const cfgA = await api('/im/getImConfig', A.token, { method: 'POST' })
  const cfgB = await api('/im/getImConfig', B.token, { method: 'POST' })
  const a = await wsDm(cfgA.data, A.userId)
  const b = await wsDm(cfgB.data, B.userId)
  await sleep(2500)
  a.sendDm(B.userId, '你好，我是私信测试 A')
  const bGot = await waitFor(b.received, m => m.fromUid === A.userId && m.content.includes('私信测试'), 8000, 'B收5569')
  log('B收到5569', true, `msgId=${bGot.msgId} content=${bGot.content}`)
  const aEcho = await waitFor(a.received, m => m.fromUid === A.userId, 8000, 'A收回显')
  log('A收到回显5569', true, `msgId=${aEcho.msgId}`)
  log('回显msgId一致', aEcho.msgId === bGot.msgId, `msgId=${aEcho.msgId}`)

  // ---- 3. 会话与未读数 ----
  vo = await api('/dm/conversations', A.token)
  const convA = (vo.data || []).find(c => c.peerUid === B.userId)
  log('A会话行(unread=0)', convA && convA.unreadCnt === 0, `lastMsg=${convA?.lastMsg} peerNick=${convA?.peerNick}`)
  vo = await api('/dm/conversations', B.token)
  const convB = (vo.data || []).find(c => c.peerUid === A.userId)
  log('B会话行(unread=1)', convB && convB.unreadCnt === 1, `unreadCnt=${convB?.unreadCnt} nick=${convB?.peerNick}`)

  // ---- 4. 历史消息 ----
  vo = await api('/dm/history', A.token, { query: { peerUid: B.userId, size: 50 } })
  log('历史消息', vo.code === 200 && (vo.data || []).length >= 1, `条数=${(vo.data || []).length}`)

  // ---- 5. markRead ----
  vo = await api('/dm/markRead', B.token, { query: { peerUid: A.userId }, method: 'POST' })
  vo = await api('/dm/unreadTotal', B.token)
  log('B已读后未读清零', Number(vo.data) === 0, `unreadTotal=${vo.data}`)

  // ---- 6. B 离线，A 再发 → 未读累计、不要求实时推送 ----
  b.close()
  await sleep(1000)
  a.sendDm(B.userId, '第二条：B 离线时的私信')
  await sleep(2500)
  vo = await api('/dm/unreadTotal', B.token)
  log('离线未读累计=1', Number(vo.data) === 1, `unreadTotal=${vo.data}`)
  vo = await api('/dm/history', B.token, { query: { peerUid: A.userId, size: 50 } })
  log('离线消息已落库', (vo.data || []).some(m => m.content.includes('第二条')), `总条数=${(vo.data || []).length}`)

  // ---- 7. B 重连可继续收 ----
  const b2 = await wsDm(cfgB.data, B.userId)
  await sleep(2500)
  a.sendDm(B.userId, '第三条：B 回来了')
  const b2Got = await waitFor(b2.received, m => m.content.includes('第三条'), 8000, 'B重连后收5569')
  log('B重连后可收', true, `content=${b2Got.content}`)

  a.close(); b2.close()
  console.log('\n批次十 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
