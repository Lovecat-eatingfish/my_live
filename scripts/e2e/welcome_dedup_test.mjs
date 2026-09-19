/**
 * 欢迎消息去重 E2E：同一天内同一用户重复进出同一房间，欢迎弹幕只广播一次。
 * 断线重连场景（offline 移除 → online 重新 added==1）不应重复欢迎。
 * 用法: node scripts/e2e/welcome_dedup_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'

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

async function wsJoin(cfg, userId, roomId, received) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: 19231, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: 19231, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      try { received.push(JSON.parse(body)) } catch { }
    } catch { }
  }
  await sleep(800)
  return { ws, close: () => { clearInterval(hbTimer); ws.close() } }
}

const countWelcome = (list, nick) =>
  list.filter(m => m.data && String(m.data).includes('欢迎') && String(m.data).includes(nick)).length

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  log('login', true, `A=${A.userId} B=${B.userId}`)

  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '欢迎去重测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '欢迎去重测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const nick = '旗鱼用户-' + A.userId
  const cfgA = await api('/im/getImConfig', A.token)

  // 第一次进房：应收到欢迎
  const got1 = []
  const a1 = await wsJoin(cfgA.data, A.userId, roomId, got1)
  await sleep(4000)
  a1.close()
  const w1 = countWelcome(got1, nick)
  log('首次进房收到欢迎', w1 >= 1, `welcome=${w1}`)

  // 断线重连：不应再收到欢迎
  await sleep(1500)
  const got2 = []
  const a2 = await wsJoin(cfgA.data, A.userId, roomId, got2)
  await sleep(4000)
  a2.close()
  const w2 = countWelcome(got2, nick)
  log('重连不重复欢迎', w2 === 0, `welcome=${w2}`)

  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n欢迎去重 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
